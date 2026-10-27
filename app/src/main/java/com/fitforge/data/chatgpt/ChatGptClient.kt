package com.fitforge.data.chatgpt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class CoachMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val hasPhotoAttached: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatGptModel(
    val slug: String,
    val displayName: String
)

object ChatGptClient {
    private const val RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
    private const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"

    /**
     * Fetches the signed-in account's available model catalog via GET https://api.openai.com/v1/models.
     * Filters for visibility == "list" as documented for Sign in with ChatGPT (SIWC),
     * preserving server order.
     */
    suspend fun fetchAvailableModels(accessToken: String): Result<List<ChatGptModel>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(MODELS_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                conn.disconnect()
                return@withContext Result.failure(Exception("Failed to fetch ChatGPT models ($responseCode): $errorBody"))
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val modelsArray = json.optJSONArray("models") ?: json.optJSONArray("data")
            val list = mutableListOf<ChatGptModel>()

            if (modelsArray != null) {
                for (i in 0 until modelsArray.length()) {
                    val item = modelsArray.optJSONObject(i) ?: continue
                    val visibility = item.optString("visibility")
                    // Official SIWC recommendation: filter for visibility == "list"
                    if (visibility.isNotEmpty() && visibility != "list") {
                        continue
                    }
                    val slug = item.optString("slug").ifEmpty { item.optString("id") }
                    if (slug.isNotBlank()) {
                        val displayName = item.optString("display_name").ifEmpty {
                            item.optString("name").ifEmpty { slug }
                        }
                        list.add(ChatGptModel(slug = slug, displayName = displayName))
                    }
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a prompt and fitness context to ChatGPT via the OpenAI Responses API
     * using the authenticated user's ChatGPT subscription token.
     *
     * Streaming tokens are emitted through onDelta callback in real time.
     */
    suspend fun streamCoachResponse(
        context: Context,
        accessToken: String,
        userPrompt: String,
        fitnessContext: String,
        photoPath: String? = null,
        photoPaths: List<String> = emptyList(),
        conversationHistory: List<CoachMessage> = emptyList(),
        modelSlug: String? = null,
        onDelta: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Dynamically resolve supported model slug from account catalog if not provided
            val resolvedModel = if (!modelSlug.isNullOrBlank()) {
                modelSlug
            } else {
                val modelsResult = fetchAvailableModels(accessToken)
                val firstAvailable = modelsResult.getOrNull()?.firstOrNull()?.slug
                if (firstAvailable.isNullOrBlank()) {
                    return@withContext Result.failure(
                        Exception("No supported ChatGPT model available for this account. Please verify your ChatGPT plan or reconnect.")
                    )
                }
                firstAvailable
            }

            val url = URL(RESPONSES_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 45000

            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")

            // Instructions giving ChatGPT coach persona with explicit safety boundaries & medical guardrails
            val instructions = """
                You are FitForge AI Fitness & Habit Coach, an informational wellness and training assistant.
                You provide practical workout motivation, progressive overload principles, general macronutrient awareness, and workout set analysis based on the user's logged FitForge data.

                IMPORTANT BOUNDARIES & SAFETY GUIDELINES:
                - You are NOT a medical doctor, registered dietitian, or licensed physical therapist.
                - Do not provide clinical medical diagnosis, prescription, or clinical dietary treatment.
                - If the user reports acute pain, injury, eating disorder behaviors, or unsafe/extreme caloric deficits (<1200 kcal/day), firmly and compassionately advise them to consult a qualified physician or healthcare professional.
                - When progress photos (front, back, and/or side views) are provided, evaluate posture, spinal alignment, front-to-back balance, muscular symmetry, and habit consistency respectfully, constructively, and objectively. Keep responses concise and formatted with clean bullet points.
            """.trimIndent()

            // Build request payload according to Responses API preview specification
            val root = JSONObject()
            root.put("model", resolvedModel)
            root.put("instructions", instructions)
            root.put("store", false) // Required by SIWC direct token usage
            root.put("stream", true) // Required by SIWC direct token usage

            val inputList = JSONArray()

            // Add previous recent messages (up to 4 for context)
            val recentHistory = conversationHistory.takeLast(4)
            for (msg in recentHistory) {
                if (msg.text.isBlank()) continue
                val histItem = JSONObject()
                histItem.put("role", if (msg.isUser) "user" else "assistant")
                histItem.put("content", msg.text)
                inputList.put(histItem)
            }

            // Current prompt with live fitness context
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")

            val combinedText = buildString {
                if (fitnessContext.isNotBlank()) {
                    appendLine("=== FITFORGE LIVE USER STATS ===")
                    appendLine(fitnessContext)
                    appendLine("================================")
                    appendLine()
                }
                appendLine("User Question / Instruction:")
                appendLine(userPrompt)
            }

            // Optional multimodal photos attached (front, back, and side views altogether)
            val allPhotos = (photoPaths + listOfNotNull(photoPath)).filter { it.isNotBlank() }.distinct()
            if (allPhotos.isNotEmpty()) {
                val contentArr = JSONArray()
                val textObj = JSONObject().apply {
                    put("type", "input_text")
                    put("text", combinedText)
                }
                contentArr.put(textObj)

                for (path in allPhotos) {
                    val base64DataUri = loadAndCompressImageBase64(context, path)
                    if (base64DataUri != null) {
                        val imageObj = JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", base64DataUri)
                        }
                        contentArr.put(imageObj)
                    }
                }

                currentTurn.put("content", contentArr)
            } else {
                currentTurn.put("content", combinedText)
            }

            inputList.put(currentTurn)
            root.put("input", inputList)

            // Send payload
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(root.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                conn.disconnect()
                val parsedError = try {
                    val errJson = JSONObject(errorBody)
                    errJson.optJSONObject("error")?.optString("message") ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }

                val userFriendlyMessage = when (responseCode) {
                    401 -> "Your ChatGPT session has expired or is unauthorized. Please sign in again in AI Coach."
                    403 -> "Access forbidden for this model or account plan. Please verify your ChatGPT subscription."
                    429 -> "ChatGPT usage limit or rate limit reached. Please wait a moment before sending another prompt."
                    in 500..599 -> "ChatGPT service is temporarily unavailable ($responseCode). Please try again shortly."
                    else -> "ChatGPT API error ($responseCode): $parsedError"
                }
                return@withContext Result.failure(Exception(userFriendlyMessage))
            }

            // Read SSE stream
            val fullResponseBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))

            var line: String?
            var streamError: Exception? = null
            val maxChars = 40000

            while (reader.readLine().also { line = it } != null) {
                // Cooperative cancellation check
                if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) {
                    reader.close()
                    conn.disconnect()
                    throw kotlinx.coroutines.CancellationException("Streaming cancelled by user")
                }

                val currentLine = line ?: break
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        break
                    }
                    if (data.isBlank()) continue

                    try {
                        val json = JSONObject(data)
                        val type = json.optString("type")

                        // Handle failure event sent over SSE
                        if (type == "response.failed") {
                            val respObj = json.optJSONObject("response")
                            val errObj = respObj?.optJSONObject("error")
                            val code = errObj?.optString("code") ?: "unknown_error"
                            val msg = errObj?.optString("message") ?: "Response failed"

                            val friendlyMsg = when (code) {
                                "subscription_sharing_usage_limit_exceeded" ->
                                    "ChatGPT plan usage limit reached for this period. Please try again later."
                                "subscription_sharing_usage_unavailable" ->
                                    "ChatGPT plan usage is currently unavailable. Please try again shortly."
                                else -> "ChatGPT error: $msg ($code)"
                            }
                            streamError = Exception(friendlyMsg)
                            break
                        }

                        // Terminal event
                        if (type == "response.completed") {
                            break
                        }

                        val deltaText = extractDeltaText(json)
                        if (!deltaText.isNullOrEmpty()) {
                            fullResponseBuilder.append(deltaText)
                            onDelta(deltaText)
                            if (fullResponseBuilder.length >= maxChars) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore malformed individual SSE chunks
                    }
                }
            }

            reader.close()
            conn.disconnect()

            if (streamError != null) {
                return@withContext Result.failure(streamError)
            }

            val completeOutput = fullResponseBuilder.toString().trim()
            if (completeOutput.isBlank()) {
                Result.failure(Exception("ChatGPT stream completed with no usable output. Please check your network and try again."))
            } else {
                Result.success(completeOutput)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts incremental text chunk from various SSE JSON delta formats.
     */
    private fun extractDeltaText(json: JSONObject): String? {
        // OpenAI Responses API: {"type":"response.output_text.delta","delta":"..."}
        val type = json.optString("type")
        if (type == "response.output_text.delta") {
            val delta = json.opt("delta")
            if (delta is String) return delta
            if (delta is JSONObject) {
                return delta.optString("text").ifEmpty { delta.optString("content") }
            }
        }

        // Generic delta string
        val deltaObj = json.opt("delta")
        if (deltaObj is String && deltaObj.isNotEmpty()) {
            return deltaObj
        }
        if (deltaObj is JSONObject) {
            val text = deltaObj.optString("text")
            if (text.isNotEmpty()) return text
            val content = deltaObj.optString("content")
            if (content.isNotEmpty()) return content
        }

        // Chat completion SSE format choices[0].delta.content
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.optJSONObject(0)
            val d = choice?.optJSONObject("delta")
            val content = d?.optString("content")
            if (!content.isNullOrEmpty()) return content
        }

        // Direct text output
        val text = json.optString("text")
        if (text.isNotEmpty()) return text

        return null
    }

    /**
     * Reads and downsamples user progress photo to base64 data URI (under 1280px max dimension).
     */
    fun loadAndCompressImageBase64(context: Context, pathOrUri: String): String? {
        return try {
            val inputStream = if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (!f.exists()) return null
                f.inputStream()
            } ?: return null

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val maxDimension = 1200
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val targetW = (width * ratio).toInt().coerceAtLeast(1)
                val targetH = (height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val bytes = baos.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
