package com.fitforge.data.chatgpt

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class ChatGptSession(
    val email: String,
    val name: String,
    val clientId: String,
    val hostId: String,
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAt: Long
)

object ChatGptAuthManager {
    private const val PREFS_NAME = "fitforge_chatgpt_prefs"
    private const val SECURE_PREFS_NAME = "fitforge_chatgpt_secure_prefs"
    private const val DEFAULT_PORT = 14555
    private const val AGENT_NAME_HINT = "FitForge"
    private const val RESOURCE = "https://api.openai.com/v1"
    private const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"

    private val secureRandom = SecureRandom()

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyTokensIfNeeded(context: Context) {
        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (plainPrefs.contains("access_token")) {
            val accessToken = plainPrefs.getString("access_token", null)
            val refreshToken = plainPrefs.getString("refresh_token", null)
            val idToken = plainPrefs.getString("id_token", null)

            val securePrefs = getSecurePrefs(context)
            if (securePrefs !== plainPrefs) {
                securePrefs.edit()
                    .putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .putString("id_token", idToken)
                    .apply()

                plainPrefs.edit()
                    .remove("access_token")
                    .remove("refresh_token")
                    .remove("id_token")
                    .apply()
            }
        }
    }

    fun clearPendingAuthState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("pending_code_verifier")
            .remove("pending_redirect_uri")
            .remove("pending_client_id")
            .remove("pending_state")
            .remove("pending_auth_code")
            .apply()
    }

    fun getHostId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var hostId = prefs.getString("ext_agent_host_id", null)
        if (hostId.isNullOrBlank()) {
            hostId = "urn:uuid:" + UUID.randomUUID().toString()
            prefs.edit().putString("ext_agent_host_id", hostId).apply()
        }
        return hostId
    }

    fun getSession(context: Context): ChatGptSession? {
        migrateLegacyTokensIfNeeded(context)
        val securePrefs = getSecurePrefs(context)
        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val token = securePrefs.getString("access_token", null)
            ?: plainPrefs.getString("access_token", null)
            ?: return null

        val email = plainPrefs.getString("user_email", "ChatGPT User") ?: "ChatGPT User"
        val name = plainPrefs.getString("user_name", "User") ?: "User"
        val clientId = plainPrefs.getString("client_id", "dynamic_agent_client") ?: "dynamic_agent_client"
        val hostId = getHostId(context)
        val refreshToken = securePrefs.getString("refresh_token", null) ?: plainPrefs.getString("refresh_token", null)
        val idToken = securePrefs.getString("id_token", null) ?: plainPrefs.getString("id_token", null)
        val expiresAt = plainPrefs.getLong("expires_at", 0L)

        return ChatGptSession(
            email = email,
            name = name,
            clientId = clientId,
            hostId = hostId,
            accessToken = token,
            refreshToken = refreshToken,
            idToken = idToken,
            expiresAt = expiresAt
        )
    }

    fun signOut(context: Context) {
        val securePrefs = getSecurePrefs(context)
        securePrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("id_token")
            .apply()

        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        plainPrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("id_token")
            .remove("user_email")
            .remove("user_name")
            .remove("expires_at")
            .remove("client_id")
            .apply()

        clearPendingAuthState(context)
    }

    /**
     * Starts the Sign in with ChatGPT OAuth 2.0 flow with PKCE and an HTTP loopback listener on 127.0.0.1.
     * Launches the system browser and awaits the authorization callback.
     */
    /**
     * Starts the Sign in with ChatGPT OAuth 2.0 flow with PKCE and an HTTP loopback listener on 127.0.0.1.
     * Launches the system browser and awaits the authorization callback.
     */
    suspend fun startSignInFlow(
        context: Context,
        onBrowserLaunch: (Intent) -> Unit
    ): Result<ChatGptSession> = withContext(Dispatchers.IO) {
        runCatching {
            val hostId = getHostId(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedClientId = prefs.getString("client_id", null)

            val port = findAvailablePort(DEFAULT_PORT)
            val redirectUri = "http://127.0.0.1:$port/auth/callback"

            // 1. Generate fresh PKCE, state, and nonce
            val codeVerifier = generateRandomString(64)
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val state = generateRandomString(32)
            val nonce = generateRandomString(32)

            val serverSocket = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            serverSocket.soTimeout = 180000 // 3 minutes timeout for user to sign in

            // 2. Build Authorization URL
            val isFirstTime = savedClientId.isNullOrBlank()
            val effectiveClientId = if (isFirstTime) "dynamic_agent_client" else savedClientId

            // Persist pending credentials so they survive process switches / network retries
            prefs.edit()
                .putString("pending_code_verifier", codeVerifier)
                .putString("pending_redirect_uri", redirectUri)
                .putString("pending_client_id", effectiveClientId)
                .putString("pending_state", state)
                .remove("pending_auth_code")
                .apply()

            val authUrlBuilder = StringBuilder("https://auth.openai.com/api/accounts/authorize")
                .append("?client_id=").append(URLEncoder.encode(effectiveClientId, "UTF-8"))
                .append("&response_type=code")
                .append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
                .append("&scope=").append(URLEncoder.encode(SCOPES, "UTF-8"))
                .append("&resource=").append(URLEncoder.encode(RESOURCE, "UTF-8"))
                .append("&ext_agent_host_id=").append(URLEncoder.encode(hostId, "UTF-8"))
                .append("&state=").append(URLEncoder.encode(state, "UTF-8"))
                .append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"))
                .append("&code_challenge=").append(URLEncoder.encode(codeChallenge, "UTF-8"))
                .append("&code_challenge_method=S256")

            if (isFirstTime) {
                authUrlBuilder.append("&agent_name_hint=").append(URLEncoder.encode(AGENT_NAME_HINT, "UTF-8"))
            }

            // Launch Browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrlBuilder.toString())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                onBrowserLaunch(browserIntent)
            }

            // 3. Await redirect on loopback server socket
            var socket: Socket? = null
            var authCode: String? = null
            var returnedClientId: String? = null

            try {
                socket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val firstLine = reader.readLine() ?: throw Exception("Empty HTTP request from browser")

                // e.g. GET /auth/callback?code=abc&state=xyz&client_id=oaiapp_123 HTTP/1.1
                val requestParts = firstLine.split(" ")
                if (requestParts.size >= 2) {
                    val requestPath = requestParts[1]
                    val uri = Uri.parse("http://127.0.0.1$requestPath")

                    val returnedState = uri.getQueryParameter("state")
                    val error = uri.getQueryParameter("error")

                    if (error != null) {
                        sendHttpResponse(socket, false, "Authorization error: $error")
                        throw Exception("OpenAI returned error: $error")
                    }

                    if (returnedState != state) {
                        sendHttpResponse(socket, false, "State mismatch error")
                        throw Exception("State validation failed")
                    }

                    authCode = uri.getQueryParameter("code")
                    returnedClientId = uri.getQueryParameter("client_id")

                    if (!authCode.isNullOrBlank()) {
                        prefs.edit().putString("pending_auth_code", authCode).apply()
                    }

                    sendHttpResponse(socket, true, "Sign in with ChatGPT successful! You can return to FitForge.")
                }
            } finally {
                socket?.close()
                serverSocket.close()
            }

            if (authCode.isNullOrBlank()) {
                throw Exception("Failed to obtain authorization code from OpenAI callback")
            }

            val finalClientId = returnedClientId ?: savedClientId ?: "dynamic_agent_client"

            // 4. Exchange authorization code for tokens (with automatic network/DNS retry)
            exchangeCodeForTokens(
                context = context,
                code = authCode,
                clientId = finalClientId,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                hostId = hostId
            )
        }.onFailure {
            clearPendingAuthState(context)
        }
    }

    fun hasPendingAuth(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString("pending_auth_code", null).isNullOrBlank() ||
                !prefs.getString("pending_code_verifier", null).isNullOrBlank()
    }

    suspend fun retryPendingTokenExchange(context: Context): Result<ChatGptSession> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getString("pending_auth_code", null)
                ?: throw Exception("No pending authorization code saved. Please tap Continue with ChatGPT.")
            val codeVerifier = prefs.getString("pending_code_verifier", null)
                ?: throw Exception("No pending code verifier found. Please tap Continue with ChatGPT.")
            val redirectUri = prefs.getString("pending_redirect_uri", "http://127.0.0.1:$DEFAULT_PORT/auth/callback")
                ?: "http://127.0.0.1:$DEFAULT_PORT/auth/callback"
            val clientId = prefs.getString("pending_client_id", "dynamic_agent_client") ?: "dynamic_agent_client"
            val hostId = getHostId(context)

            exchangeCodeForTokens(
                context = context,
                code = code,
                clientId = clientId,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                hostId = hostId
            )
        }
    }

    suspend fun completeWithCallbackUrl(context: Context, callbackUrlOrCode: String): Result<ChatGptSession> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = callbackUrlOrCode.trim()
            val uri = if (trimmed.contains("://")) Uri.parse(trimmed) else null
            val code = uri?.getQueryParameter("code") ?: if (trimmed.startsWith("oaistb_")) trimmed else null
            if (code.isNullOrBlank()) {
                throw Exception("Invalid callback format. Please provide the full URL containing code=...")
            }
            val returnedClientId = uri?.getQueryParameter("client_id")

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val codeVerifier = prefs.getString("pending_code_verifier", null)
                ?: throw Exception("No pending code verifier on this device. Please tap 'Continue with ChatGPT' to initialize.")
            val redirectUri = prefs.getString("pending_redirect_uri", "http://127.0.0.1:$DEFAULT_PORT/auth/callback")
                ?: "http://127.0.0.1:$DEFAULT_PORT/auth/callback"
            val finalClientId = returnedClientId ?: prefs.getString("pending_client_id", null) ?: prefs.getString("client_id", "dynamic_agent_client") ?: "dynamic_agent_client"
            val hostId = getHostId(context)

            exchangeCodeForTokens(
                context = context,
                code = code,
                clientId = finalClientId,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                hostId = hostId
            )
        }.onFailure {
            clearPendingAuthState(context)
        }
    }

    private fun exchangeCodeForTokens(
        context: Context,
        code: String,
        clientId: String,
        codeVerifier: String,
        redirectUri: String,
        hostId: String
    ): ChatGptSession {
        val responseBody = executeWithNetworkRetry(maxAttempts = 5, initialDelayMs = 1500L) {
            val url = URL("https://auth.openai.com/api/accounts/oauth/token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

            val params = listOf(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to codeVerifier,
                "redirect_uri" to redirectUri,
                "resource" to RESOURCE
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(params) }

            val codeResponse = conn.responseCode
            val stream = if (codeResponse in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            if (codeResponse !in 200..299) {
                throw Exception("Token exchange failed ($codeResponse): $body")
            }
            body
        }

        val json = JSONObject(responseBody)
        val accessToken = json.getString("access_token")
        val refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null
        val idToken = if (json.has("id_token")) json.getString("id_token") else null
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

        // Parse claims from id_token
        var userEmail = "ChatGPT User"
        var userName = "User"
        if (!idToken.isNullOrBlank()) {
            runCatching {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING)
                    val payloadJson = JSONObject(String(decodedBytes, StandardCharsets.UTF_8))
                    userEmail = payloadJson.optString("email", userEmail)
                    userName = payloadJson.optString("name", userName)
                }
            }
        }

        // Persist tokens securely using Android Keystore
        val securePrefs = getSecurePrefs(context)
        securePrefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("id_token", idToken)
            .apply()

        // Persist non-sensitive metadata in regular prefs
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("client_id", clientId)
            .putString("user_email", userEmail)
            .putString("user_name", userName)
            .putLong("expires_at", expiresAt)
            .remove("access_token") // Ensure plain storage does not retain raw tokens
            .remove("refresh_token")
            .remove("id_token")
            .apply()

        // Clear temporary authorization state on successful token exchange
        clearPendingAuthState(context)

        return ChatGptSession(
            email = userEmail,
            name = userName,
            clientId = clientId,
            hostId = hostId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            expiresAt = expiresAt
        )
    }

    private fun <T> executeWithNetworkRetry(
        maxAttempts: Int = 5,
        initialDelayMs: Long = 1500L,
        block: () -> T
    ): T {
        var lastException: Exception? = null
        var delayMs = initialDelayMs
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val isDnsOrNetwork = e is java.net.UnknownHostException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException ||
                        e.message?.contains("Unable to resolve host", true) == true
                if (!isDnsOrNetwork || attempt == maxAttempts) {
                    throw e
                }
                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    throw e
                }
                delayMs = (delayMs * 1.5).toLong()
            }
        }
        throw lastException ?: Exception("Network request failed after $maxAttempts attempts")
    }

    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val session = getSession(context) ?: return@withContext null
        val now = System.currentTimeMillis()
        if (now < session.expiresAt - 60000) {
            return@withContext session.accessToken
        }

        // Refresh token if expired
        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            if (now >= session.expiresAt) {
                signOut(context)
                return@withContext null
            }
            return@withContext session.accessToken
        }

        try {
            val url = URL("https://auth.openai.com/api/accounts/oauth/token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

            val params = listOf(
                "grant_type" to "refresh_token",
                "client_id" to session.clientId,
                "refresh_token" to refreshToken,
                "resource" to RESOURCE
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(params) }
            val codeResponse = conn.responseCode
            if (codeResponse in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val expiresIn = json.optLong("expires_in", 3600L)
                val newExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                val securePrefs = getSecurePrefs(context)
                securePrefs.edit()
                    .putString("access_token", newAccessToken)
                    .putString("refresh_token", newRefreshToken)
                    .apply()

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putLong("expires_at", newExpiresAt)
                    .apply()

                newAccessToken
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()

                // If refresh token is revoked/invalid, sign out and prompt reconnect instead of returning stale token
                if (codeResponse == 400 || codeResponse == 401 || codeResponse == 403 || errorStream.contains("invalid_grant", ignoreCase = true)) {
                    signOut(context)
                    null
                } else if (now < session.expiresAt) {
                    session.accessToken
                } else {
                    signOut(context)
                    null
                }
            }
        } catch (e: Exception) {
            if (now < session.expiresAt) {
                session.accessToken
            } else {
                signOut(context)
                null
            }
        }
    }

    private fun findAvailablePort(startPort: Int): Int {
        for (port in startPort until startPort + 50) {
            try {
                val ss = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
                ss.close()
                return port
            } catch (e: Exception) {
                // port busy, check next
            }
        }
        return startPort
    }

    private fun sendHttpResponse(socket: Socket, success: Boolean, message: String) {
        val title = if (success) "✓ FitForge Authorized" else "✕ Authorization Error"
        val color = if (success) "#9FE870" else "#FF6B6B"
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>$title</title></head>
            <body style="font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background:#101310;color:#F5F5F5;display:flex;align-items:center;justify-content:center;height:90vh;margin:0;">
                <div style="background:#1A201B;padding:36px;border-radius:18px;text-align:center;max-width:380px;box-shadow:0 8px 32px rgba(0,0,0,0.5);">
                    <h2 style="color:$color;margin-top:0;">$title</h2>
                    <p style="color:#A0AAB0;font-size:15px;line-height:1.5;">$message</p>
                    <p style="font-size:13px;color:#718096;margin-top:24px;">You can close this tab and return to the FitForge app.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
                "Connection: close\r\n\r\n" + html

        OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8).use {
            it.write(response)
            it.flush()
        }
    }

    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).take(length)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
