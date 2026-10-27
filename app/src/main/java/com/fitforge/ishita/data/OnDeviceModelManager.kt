package com.fitforge.ishita.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class OnDeviceModelManager(private val context: Context) {

    companion object {
        const val MODEL_FILENAME = "gemmasutra_mini_2b_q4_k_m.gguf"
        const val MODEL_DISPLAY_NAME = "Gemmasutra Mini 2B (Q4_K_M)"
        // Direct HuggingFace GGUF link for Gemmasutra Mini 2B
        const val DEFAULT_DOWNLOAD_URL =
            "https://huggingface.co/mradermacher/Gemmasutra-Mini-2B-v1-GGUF/resolve/main/Gemmasutra-Mini-2B-v1.Q4_K_M.gguf?download=true"
    }

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val modelFile: File
        get() = File(modelsDir, MODEL_FILENAME)

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() > 50_000_000L // at least 50MB
    }

    fun getModelFileSizeFormatted(): String {
        if (!modelFile.exists()) return "0 MB"
        val bytes = modelFile.length()
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    /**
     * Downloads the model file with real-time byte progress.
     */
    suspend fun downloadModel(
        downloadUrl: String = DEFAULT_DOWNLOAD_URL,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progressPercent: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(modelsDir, "$MODEL_FILENAME.tmp")
        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var currentUrl = downloadUrl
            var redirectCount = 0

            // Handle HTTP 301 / 302 / 307 redirects for Hugging Face CDN
            while (redirectCount < 6) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "FitForge-Android/1.1")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location != null) {
                        currentUrl = location
                        redirectCount++
                        continue
                    }
                }
                break
            }

            if (conn == null || conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("Failed to download model: HTTP ${conn?.responseCode ?: "unknown"}")
                )
            }

            val totalBytes = conn.contentLengthLong
            inputStream = conn.inputStream
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(64 * 1024) // 64 KB buffer
            var bytesDownloaded = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesDownloaded += read
                val percent = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                onProgress(bytesDownloaded, totalBytes, percent)
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null
            inputStream.close()
            inputStream = null

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Imports a user-selected GGUF file from device storage via content URI.
     */
    suspend fun importModelFromUri(
        sourceUri: Uri,
        onProgress: (bytesCopied: Long, percent: Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(modelsDir, "$MODEL_FILENAME.tmp")
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open selected file stream"))

            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(64 * 1024)
            var bytesCopied = 0L
            var read: Int

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        onProgress(bytesCopied, 0f)
                    }
                    output.flush()
                }
            }

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    fun deleteModel(): Boolean {
        return if (modelFile.exists()) {
            modelFile.delete()
        } else false
    }
}
