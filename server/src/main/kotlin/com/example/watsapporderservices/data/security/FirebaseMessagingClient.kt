package com.example.watsapporderservices.data.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
private const val TOKEN_TTL_MILLIS = 55 * 60 * 1000L
private const val TIMEOUT_SECONDS = 15L

/**
 * Sends Firebase Cloud Messaging (HTTP v1) notifications using a service account.
 * Builds a signed JWT, exchanges it for an OAuth access token and posts the message.
 */
class FirebaseMessagingClient(
    private val config: PushConfig,
) {
    private val logger = LoggerFactory.getLogger(FirebaseMessagingClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedTokenExpiresAt: Long = 0

    val isConfigured: Boolean get() = config.isConfigured

    /** Sends a data notification to one device token. Returns true when FCM accepted it. */
    fun sendToToken(token: String, title: String, body: String, data: Map<String, String> = emptyMap()): Boolean {
        val accessToken = accessToken() ?: return false

        val message = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("notification") {
                    put("title", title)
                    put("body", body)
                }
                putJsonObject("data") {
                    data.forEach { (key, value) -> put(key, value) }
                }
            }
        }
        val request = HttpRequest.newBuilder(
            URI.create("https://fcm.googleapis.com/v1/projects/${config.projectId}/messages:send"),
        )
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(message.toString()))
            .build()

        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return false

        if (response.statusCode() !in 200..299) {
            logger.warn("FCM send failed ({}): {}", response.statusCode(), response.body().take(300))
            return false
        }
        return true
    }

    private fun accessToken(): String? {
        val now = System.currentTimeMillis()
        cachedAccessToken?.let { if (now < cachedTokenExpiresAt) return it }

        val account = config.serviceAccount ?: return null
        val assertion = runCatching { buildAssertion(account) }.getOrNull() ?: return null

        val form = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$assertion"
        val request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null

        if (response.statusCode() !in 200..299) {
            logger.warn("FCM token exchange failed ({}): {}", response.statusCode(), response.body().take(300))
            return null
        }

        val token = runCatching {
            json.parseToJsonElement(response.body()).jsonObject["access_token"]?.jsonPrimitive?.content
        }.getOrNull() ?: return null

        cachedAccessToken = token
        cachedTokenExpiresAt = now + TOKEN_TTL_MILLIS
        return token
    }

    private fun buildAssertion(account: FirebaseServiceAccount): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val claims = base64Url(
            """{"iss":"${account.clientEmail}","scope":"$FCM_SCOPE","aud":"$TOKEN_URL",""" +
                """"iat":$nowSeconds,"exp":${nowSeconds + 3600}}""",
        )
        val signingInput = "$header.$claims"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey(account.privateKey))
            update(signingInput.toByteArray())
        }.sign()
        return "$signingInput.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
    }

    private fun privateKey(pem: String): RSAPrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace(Regex("\\s"), "")
        val encoded = Base64.getDecoder().decode(cleaned)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded)) as RSAPrivateKey
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
