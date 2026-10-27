package com.fitforge.ishita.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fitforge.data.chatgpt.CoachMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class BridgeHealth(
    val status: String,
    val ollamaConnected: Boolean,
    val activeModel: String,
    val availableModels: List<String>
)

data class ProposedAction(
    val id: String,
    val type: String, // "BLOCK_APP", "BLOCK_WEBSITE", "SCHEDULE_MAKEUP", "CREATE_REMINDER"
    val title: String,
    val reason: String,
    val payload: JSONObject
)

sealed interface IshitaEvent {
    data object Connected : IshitaEvent
    data class TextDelta(val delta: String) : IshitaEvent
    data class ProposalReceived(val proposal: ProposedAction) : IshitaEvent
    data class Error(val message: String) : IshitaEvent
    data object Done : IshitaEvent
}

class IshitaClient(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "fitforge_ishita_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("fitforge_ishita_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_BRIDGE_URL = "key_bridge_url"
        private const val KEY_TOKEN = "key_token"
        const val DEFAULT_BRIDGE_URL = "http://10.0.2.2:8765"
    }

    fun getBridgeUrl(): String {
        return prefs.getString(KEY_BRIDGE_URL, DEFAULT_BRIDGE_URL) ?: DEFAULT_BRIDGE_URL
    }

    fun setBridgeUrl(url: String) {
        var cleaned = url.trim()
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "http://$cleaned"
        }
        prefs.edit().putString(KEY_BRIDGE_URL, cleaned).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun isConnected(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun disconnect() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /**
     * Checks health and connection of the Mac bridge and Ollama.
     */
    suspend fun checkHealth(): Result<BridgeHealth> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBridgeUrl()}/v1/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val modelsArr = obj.optJSONArray("availableModels") ?: JSONArray()
                val models = (0 until modelsArr.length()).map { modelsArr.getString(it) }
                Result.success(
                    BridgeHealth(
                        status = obj.optString("status", "unknown"),
                        ollamaConnected = obj.optBoolean("ollamaConnected", false),
                        activeModel = obj.optString("activeModel", "none"),
                        availableModels = models
                    )
                )
            } else {
                Result.failure(Exception("Bridge returned HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pairs this device with the Mac bridge using a 6-character code.
     */
    suspend fun pair(code: String, deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBridgeUrl()}/v1/pair/complete")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true

            val reqBody = JSONObject().apply {
                put("code", code.trim().uppercase())
                put("deviceName", deviceName)
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(reqBody.toString())
                it.flush()
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val token = obj.getString("token")
                prefs.edit().putString(KEY_TOKEN, token).apply()
                Result.success(Unit)
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Pairing failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streams chat responses and proposal events from Ishita via SSE.
     */
    fun streamChat(
        message: String,
        context: AccountabilityContextDto,
        history: List<CoachMessage>
    ): Flow<IshitaEvent> = flow {
        val token = getToken()
        if (token.isNullOrBlank()) {
            emit(IshitaEvent.Error("Device not paired with Mac bridge"))
            return@flow
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL("${getBridgeUrl()}/v1/chat")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Connection", "keep-alive")
            conn.connectTimeout = 30000
            conn.readTimeout = 300000
            conn.doOutput = true

            // Build request payload
            val reqObj = JSONObject().apply {
                put("message", message)

                // Context
                val ctxObj = JSONObject().apply {
                    put("localDate", context.localDate)
                    put("plannedWorkout", context.plannedWorkout ?: JSONObject.NULL)
                    put("workoutStatus", context.workoutStatus)
                    put("completedSessionsThisWeek", context.completedSessionsThisWeek)
                    put("missedSessionsThisWeek", context.missedSessionsThisWeek)
                    put("streakDays", context.streakDays)

                    val appsArray = JSONArray()
                    context.topDistractingApps.forEach { a ->
                        appsArray.put(JSONObject().apply {
                            put("packageName", a.packageName)
                            put("appName", a.appName)
                            put("foregroundMinutes", a.foregroundMinutes)
                        })
                    }
                    put("topDistractingApps", appsArray)

                    val webArray = JSONArray()
                    context.topDistractingWebsites.forEach { w ->
                        webArray.put(JSONObject().apply {
                            put("domain", w.domain)
                            put("foregroundMinutes", w.foregroundMinutes)
                            put("visitCount", w.visitCount)
                        })
                    }
                    put("topDistractingWebsites", webArray)
                }
                put("context", ctxObj)

                // History
                val histArray = JSONArray()
                history.takeLast(6).forEach { h ->
                    histArray.put(JSONObject().apply {
                        put("role", if (h.isUser) "user" else "assistant")
                        put("content", h.text)
                    })
                }
                put("history", histArray)
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(reqObj.toString())
                it.flush()
            }

            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
                emit(IshitaEvent.Error(err))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
            var currentEvent = "message"

            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim()
                } else if (line.startsWith("data:")) {
                    val data = line.substring(5).trim()
                    when (currentEvent) {
                        "ping" -> {
                            emit(IshitaEvent.Connected)
                        }
                        "text" -> {
                            if (data.isNotBlank()) {
                                val obj = JSONObject(data)
                                val delta = obj.optString("delta", "")
                                if (delta.isNotEmpty()) {
                                    emit(IshitaEvent.TextDelta(delta))
                                }
                            }
                        }
                        "proposal" -> {
                            if (data.isNotBlank()) {
                                val obj = JSONObject(data)
                                val action = ProposedAction(
                                    id = obj.getString("id"),
                                    type = obj.getString("type"),
                                    title = obj.getString("title"),
                                    reason = obj.getString("reason"),
                                    payload = obj.getJSONObject("payload")
                                )
                                emit(IshitaEvent.ProposalReceived(action))
                            }
                        }
                        "error" -> {
                            val errText = if (data.isNotBlank()) {
                                val obj = JSONObject(data)
                                obj.optString("error", "Unknown server error")
                            } else "Unknown server error"
                            emit(IshitaEvent.Error(errText))
                        }
                        "done" -> {
                            emit(IshitaEvent.Done)
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(IshitaEvent.Error(e.localizedMessage ?: "Connection error"))
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun confirmProposal(proposalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("Not paired"))
        try {
            val url = URL("${getBridgeUrl()}/v1/actions/$proposalId/confirm")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to confirm on server: HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectProposal(proposalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext Result.failure(Exception("Not paired"))
        try {
            val url = URL("${getBridgeUrl()}/v1/actions/$proposalId/reject")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to reject on server: HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
