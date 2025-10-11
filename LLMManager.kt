package com.example.groot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LLMManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://192.168.88.39:8000"
    private val memoryManager = MemoryManager(context)  // Pass context here

    companion object {
        private const val TAG = "LLMManager"
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            memoryManager.addConversation("User: $prompt")

            val recentContext = memoryManager.getRecentContext(5)
            val contextString = recentContext.joinToString("\n")

            val jsonBody = JSONObject().apply {
                put("text", prompt)
                put("context", contextString)  // Send conversation history
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/query")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Sending request: $prompt")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code}")
                    throw IOException("Server error: ${response.code}")
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Raw response: $responseBody")

                if (responseBody.isEmpty()) {
                    throw IOException("Empty response")
                }

                val jsonResponse = JSONObject(responseBody)
                val reply = jsonResponse.optString("reply", "")
                val action = jsonResponse.optString("action", "none")
                val target = jsonResponse.optString("target", "")

                if (reply.isEmpty()) {
                    throw IOException("No reply in response")
                }

                memoryManager.addConversation("Assistant: $reply")
                Log.d(TAG, "Generated response: $reply (action=$action, target=$target)")

                return@withContext JSONObject()
                    .put("reply", reply)
                    .put("action", action)
                    .put("target", target)
                    .toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            val isHindi = prompt.matches(Regex(".*[\\u0900-\\u097F].*"))
            return@withContext if (isHindi) {
                JSONObject()
                    .put("reply", "मुझे खेद है sir, मुझे अपनी AI सर्वर से जुड़ने में समस्या हो रही है।")
                    .put("action", "none")
                    .put("target", "")
                    .toString()
            } else {
                JSONObject()
                    .put("reply", "I'm sorry, I'm having trouble connecting to my AI service.")
                    .put("action", "none")
                    .put("target", "")
                    .toString()
            }
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            Log.d(TAG, "Testing connection to: $baseUrl/health")

            client.newCall(request).execute().use { response ->
                val isSuccessful = response.isSuccessful
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "Connection test: ${response.code} - $responseBody")

                if (isSuccessful && responseBody.contains("healthy")) {
                    Log.d(TAG, "Connection successful")
                    return@withContext true
                } else {
                    Log.e(TAG, "Connection failed")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            return@withContext false
        }
    }

    fun clearMemory() {
        memoryManager.clearMemory()
        Log.d(TAG, "Memory cleared")
    }

    fun getMemoryManager(): MemoryManager {
        return memoryManager
    }

    suspend fun getConnectionInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "unknown")
                    val ollama = json.optString("ollama", "unknown")
                    "Server: $status, Ollama: $ollama"
                } else {
                    "Connection failed: ${response.code}"
                }
            }
        } catch (e: Exception) {
            "Connection error: ${e.message}"
        }
    }
}