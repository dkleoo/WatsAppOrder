package com.example.watsapporderservices.data.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

data class FirebaseUserClaims(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
    val picture: String?,
    val phoneNumber: String?,
)

private const val GOOGLE_PUBLIC_KEYS_URL =
    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
private const val DEFAULT_KEY_CACHE_SECONDS = 3600L
private const val EXPECTED_ALGORITHM = "RS256"
private const val HTTP_TIMEOUT_SECONDS = 10L

/**
 * Verifies Firebase ID tokens (JWT) against Google's public x509 certificates.
 *
 * Checks the RS256 signature, the token issuer (`https://securetoken.google.com/<projectId>`),
 * the audience (`<projectId>`) and the expiration. No Firebase Admin SDK is required.
 */
class FirebaseTokenVerifier(
    private val projectId: String,
    private val clockSkewSeconds: Long = 30,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
        .build()
    private val cachedKeys = AtomicReference<CachedKeys?>(null)

    val isConfigured: Boolean get() = projectId.isNotBlank()

    fun verify(idToken: String): FirebaseUserClaims? {
        if (!isConfigured) return null

        val decoded = runCatching { JWT.decode(idToken) }.getOrNull() ?: return null
        if (!EXPECTED_ALGORITHM.equals(decoded.algorithm, ignoreCase = true)) return null
        val keyId = decoded.keyId ?: return null
        val publicKey = publicKeyFor(keyId) ?: return null

        val verified = runCatching {
            JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(issuer)
                .withAudience(projectId)
                .acceptLeeway(clockSkewSeconds)
                .build()
                .verify(idToken)
        }.getOrNull() ?: return null

        val uid = verified.subject?.takeIf { it.isNotBlank() } ?: return null
        return FirebaseUserClaims(
            uid = uid,
            email = verified.stringClaim("email"),
            emailVerified = verified.booleanClaim("email_verified") ?: false,
            name = verified.stringClaim("name"),
            picture = verified.stringClaim("picture"),
            phoneNumber = verified.stringClaim("phone_number"),
        )
    }

    private val issuer: String get() = "https://securetoken.google.com/$projectId"

    private fun publicKeyFor(keyId: String): RSAPublicKey? {
        val cached = cachedKeys.get()
        if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) {
            cached.keys[keyId]?.let { return it }
        }
        val refreshed = fetchKeys()
        if (refreshed != null) {
            cachedKeys.set(refreshed)
            refreshed.keys[keyId]?.let { return it }
        }
        return cached?.keys?.get(keyId)
    }

    private fun fetchKeys(): CachedKeys? {
        val request = HttpRequest.newBuilder(URI.create(GOOGLE_PUBLIC_KEYS_URL))
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null
        if (response.statusCode() != 200) return null

        val pemByKeyId = runCatching {
            json.parseToJsonElement(response.body()).jsonObject
                .mapNotNull { (keyId, value) -> value.jsonPrimitive.contentOrNull?.let { keyId to it } }
                .toMap()
        }.getOrNull() ?: return null

        val keys = pemByKeyId
            .mapNotNull { (keyId, pem) -> parsePublicKey(pem)?.let { keyId to it } }
            .toMap()
        if (keys.isEmpty()) return null

        val maxAgeSeconds = response.headers().firstValue("Cache-Control").orElse("")
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("max-age=") }
            ?.substringAfter("max-age=")
            ?.toLongOrNull()
            ?: DEFAULT_KEY_CACHE_SECONDS

        return CachedKeys(keys, System.currentTimeMillis() + maxAgeSeconds * 1000)
    }

    private fun parsePublicKey(pem: String): RSAPublicKey? = runCatching {
        val base64 = pem
            .replace(CERTIFICATE_BEGIN, "")
            .replace(CERTIFICATE_END, "")
            .replace(WHITESPACE, "")
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(base64)))
        certificate.publicKey as? RSAPublicKey
    }.getOrNull()

    private data class CachedKeys(
        val keys: Map<String, RSAPublicKey>,
        val expiresAtMillis: Long,
    )

    private companion object {
        val CERTIFICATE_BEGIN = Regex("-----BEGIN CERTIFICATE-----")
        val CERTIFICATE_END = Regex("-----END CERTIFICATE-----")
        val WHITESPACE = Regex("\\s")
    }
}

private fun DecodedJWT.stringClaim(name: String): String? =
    runCatching { getClaim(name).asString() }.getOrNull()?.takeIf { it.isNotBlank() }

private fun DecodedJWT.booleanClaim(name: String): Boolean? =
    runCatching { getClaim(name).asBoolean() }.getOrNull()
