package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.notification.DeviceTokenDao
import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.database.store.Stores
import com.example.watsapporderservices.data.database.user.UserDao
import com.example.watsapporderservices.data.database.user.UserEntity
import com.example.watsapporderservices.data.database.user.Users
import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.enum.AuthProvider
import com.example.watsapporderservices.data.mapper.FederatedAuthRequest
import com.example.watsapporderservices.data.mapper.LoginRequest
import com.example.watsapporderservices.data.mapper.RegisterRequest
import com.example.watsapporderservices.data.mapper.UserResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.security.FirebaseTokenVerifier
import com.example.watsapporderservices.data.security.FirebaseUserClaims
import com.example.watsapporderservices.data.security.PasswordHasher
import com.example.watsapporderservices.data.security.TokenService
import com.example.watsapporderservices.domain.repository.AuthRepository
import com.example.watsapporderservices.domain.usecase.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

const val MIN_PASSWORD_LENGTH = 8

private const val SYNTHETIC_EMAIL_DOMAIN = "firebase.local"

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val storeDao: StoreDao,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val firebaseTokenVerifier: FirebaseTokenVerifier,
    private val deviceTokenDao: DeviceTokenDao,
) : AuthRepository {
    override suspend fun register(request: RegisterRequest): AuthResult {
        val email = request.email.trim().lowercase()
        val name = request.name.trim()
        validateCredentials(email, request.password, name)?.let { return it }
        val exists = withContext(Dispatchers.IO) { userDao.existsByEmail(email) }
        if (exists) {
            return AuthResult.EmailAlreadyRegistered
        }
        val passwordHash = passwordHasher.hash(request.password)
        val user = withContext(Dispatchers.IO) {
            createUserWithStore(email, name, passwordHash, firebaseUid = null, provider = null)
        }
        return success(user)
    }

    override suspend fun login(request: LoginRequest): AuthResult {
        val email = request.email.trim().lowercase()
        if (!EMAIL_PATTERN.matches(email) || request.password.isEmpty()) {
            return AuthResult.InvalidCredentials
        }
        val user = withContext(Dispatchers.IO) { userDao.findByEmail(email) }
            ?: return AuthResult.InvalidCredentials
        if (!passwordHasher.verify(request.password, user.passwordHash)) {
            return AuthResult.InvalidCredentials
        }
        return success(user)
    }

    override suspend fun federated(request: FederatedAuthRequest): AuthResult {
        val idToken = request.idToken?.trim().orEmpty()
        if (idToken.isEmpty()) {
            return AuthResult.InvalidInput(AuthErrorCode.INVALID_REQUEST)
        }
        val claims = firebaseTokenVerifier.verify(idToken) ?: return AuthResult.InvalidToken

        val provider = (request.provider ?: AuthProvider.EMAIL).name
        val firebaseUid = claims.uid
        val tokenEmail = claims.email?.trim()?.lowercase()?.takeIf { EMAIL_PATTERN.matches(it) }
        val email = tokenEmail ?: syntheticEmail(claims)
        val name = claims.name?.trim()?.takeIf { it.isNotBlank() }
            ?: request.name?.trim()?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')

        val byFirebaseUid = withContext(Dispatchers.IO) { userDao.findByFirebaseUid(firebaseUid) }
        if (byFirebaseUid != null) {
            val linked = withContext(Dispatchers.IO) {
                userDao.linkFederation(byFirebaseUid.id, firebaseUid, provider, email, null)
            }
            return successWithDevice(linked, request.deviceToken)
        }

        if (tokenEmail != null && claims.emailVerified) {
            val byEmail = withContext(Dispatchers.IO) { userDao.findByEmail(tokenEmail) }
            if (byEmail != null) {
                val linked = withContext(Dispatchers.IO) {
                    userDao.linkFederation(byEmail.id, firebaseUid, provider, tokenEmail, name)
                }
                return successWithDevice(linked, request.deviceToken)
            }
        } else if (tokenEmail != null) {
            // The email is already owned by another account but Firebase has not verified it: refuse to
            // link, otherwise we would risk an account takeover or a duplicate-email constraint violation.
            val conflict = withContext(Dispatchers.IO) { userDao.existsByEmail(tokenEmail) }
            if (conflict) {
                return AuthResult.InvalidToken
            }
        }

        // Federated users authenticate through Firebase, so the password hash is an unusable sentinel.
        val passwordHash = passwordHasher.hash(UUID.randomUUID().toString())
        val user = withContext(Dispatchers.IO) {
            createUserWithStore(email, name, passwordHash, firebaseUid, provider)
        }
        return successWithDevice(user, request.deviceToken)
    }

    override suspend fun profile(userId: Int): UserResponse? =
        withContext(Dispatchers.IO) { userDao.findById(userId)?.toResponse() }

    override suspend fun refresh(userId: Int): AuthResult {
        val user = withContext(Dispatchers.IO) { userDao.findById(userId) }
            ?: return AuthResult.InvalidCredentials
        return success(user)
    }

    /**
     * Creates the user and, in the same transaction, their store. The store starts with empty
     * WhatsApp/address data that the user completes later from the app (via `PUT /stores/{id}`).
     */
    private fun createUserWithStore(
        email: String,
        name: String,
        passwordHash: String,
        firebaseUid: String?,
        provider: String?,
    ): UserEntity = transaction {
        val createdAt = System.currentTimeMillis()
        val userId = Users.insert {
            it[Users.email] = email
            it[Users.name] = name
            it[Users.passwordHash] = passwordHash
            it[Users.firebaseUid] = firebaseUid
            it[Users.authProvider] = provider
            it[Users.createdAt] = createdAt
        } get Users.id

        val storeId = Stores.insert {
            it[Stores.userId] = userId
            it[Stores.welcomeMessage] = DEFAULT_WELCOME_MESSAGE
            it[Stores.address] = ""
            it[Stores.phone] = ""
            it[Stores.whatsappBusinessPhone] = ""
            it[Stores.idWhatsApp] = ""
        } get Stores.id

        Users.update({ Users.id eq userId }) { it[Users.storeId] = storeId }

        UserEntity(
            id = userId,
            email = email,
            name = name,
            passwordHash = passwordHash,
            firebaseUid = firebaseUid,
            authProvider = provider,
            storeId = storeId,
            createdAt = createdAt,
        )
    }

    /**
     * Issues the auth response and, when the client sent one, registers the FCM device token so the
     * user starts receiving push notifications without a separate `/notifications/device` call.
     */
    private suspend fun successWithDevice(user: UserEntity, deviceToken: String?): AuthResult {
        val token = deviceToken?.trim().orEmpty()
        if (token.isNotEmpty()) {
            withContext(Dispatchers.IO) { deviceTokenDao.upsert(user.id, token) }
        }
        return success(user)
    }

    private fun success(user: UserEntity): AuthResult.Success {
        val storeId = resolveStoreId(user)
        return AuthResult.Success(
            tokenService.issue(user.id, user.email, storeId),
            tokenService.expirationSeconds,
            user.toResponse().copy(storeId = storeId),
        )
    }

    /** Uses `users.store_id`, falling back to the store linked by `stores.user_id` for older users. */
    private fun resolveStoreId(user: UserEntity): Int? =
        user.storeId ?: storeDao.findByUserId(user.id)?.id

    private fun syntheticEmail(claims: FirebaseUserClaims): String {
        val localPart = claims.phoneNumber
            ?.replace(Regex("[^0-9+]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: claims.uid
        return "${localPart.lowercase()}@$SYNTHETIC_EMAIL_DOMAIN"
    }

    private fun validateCredentials(email: String, password: String, name: String): AuthResult.InvalidInput? = when {
        !EMAIL_PATTERN.matches(email) -> AuthResult.InvalidInput(AuthErrorCode.INVALID_EMAIL)
        password.length < MIN_PASSWORD_LENGTH -> AuthResult.InvalidInput(AuthErrorCode.INVALID_PASSWORD)
        name.isBlank() -> AuthResult.InvalidInput(AuthErrorCode.INVALID_NAME)
        else -> null
    }

    private companion object {
        const val DEFAULT_WELCOME_MESSAGE = "¡Hola! Bienvenido a mi tienda 🛒 ¿Qué deseas ordenar?"
    }
}
