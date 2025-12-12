package com.example.groot.email

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiEmailAssistant(private val context: Context) {

    companion object {
        private const val TAG = "GeminiEmailAssistant"
        private const val GEMINI_API_KEY = "AIzaSyBGPQuF9rz4Z0N0JF4MM7wxBoEkviqs6tQ"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Process user's speech and generate email content
     */
    suspend fun processEmailContent(
        userSpeech: String,
        recipientName: String,
        isHindi: Boolean
    ): EmailContent? = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "🤖 Processing email content for $recipientName")
            Log.d(TAG, "🗣️ User speech: ${userSpeech.take(100)}...")

            val prompt = buildPrompt(userSpeech, recipientName, isHindi)

            val response = callGeminiAPI(prompt)

            if (response != null) {
                Log.d(TAG, "✅ Gemini response received")
                return@withContext response
            } else {
                Log.e(TAG, "❌ Gemini returned null")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Email content processing failed", e)
            return@withContext null
        }
    }

    /**
     * Build prompt for Gemini API
     */
    private fun buildPrompt(userSpeech: String, recipientName: String, isHindi: Boolean): String {
        return if (isHindi) {
            /*"""
            आप एक email लिखने वाले assistant हैं।
            
            User ${recipientName} को email भेजना चाहता है।
            User ने कहा: "$userSpeech"
            
            आपका काम:
            1. User की बात से main message निकालें
            2. एक professional email subject बनाएं (maximum 8 शब्द)
            3. एक अच्छा email body बनाएं (2-4 वाक्य)
            4. User की tone maintain करें
            5. Grammar ठीक करें
            6. Clear और concise रखें
            
            Response STRICTLY इस JSON format में दें:
            {"subject": "email का subject", "body": "email का body text"}
            
            सिर्फ JSON दें, कोई extra text नहीं।
            """.trimIndent()*/
            """
            You are an email writing assistant.
            
            User wants to send email to: $recipientName
            User said: "$userSpeech"
            
            Your task:
            0.You always have to send email in English Language Only .
            If user speaks in Hindi or any Language then you have to convert $userSpeech in English Language.
            1. Extract the main message from user's speech
            2. Create a professional email subject (max 8 words)
            3. Create a well-formatted email body (2-4 sentences)
            4. Maintain the tone of original speech
            5. Fix grammar if needed
            6. Keep it concise and clear
            
            Response STRICTLY in this JSON format:
            {"subject": "email subject here", "body": "email body text here"}
            7. Always Add My Name - "Mahendra Singh Parmar" at the bottom of the Email body.
            
            ONLY JSON, no extra text.
            """.trimIndent()
        } else {
            """
            You are an email writing assistant.
            
            User wants to send email to: $recipientName
            User said: "$userSpeech"
            
            Your task:
            0.You always have to send email in English Language Only .
            If user speaks in Hindi or any Language then you have to convert $userSpeech in English Language.
            1. Extract the main message from user's speech
            2. Create a professional email subject (max 8 words)
            3. Create a well-formatted email body (2-4 sentences)
            4. Maintain the tone of original speech
            5. Fix grammar if needed
            6. Keep it concise and clear
            
            Response STRICTLY in this JSON format:
            {"subject": "email subject here", "body": "email body text here"}
            7. Always Add My Name - "Mahendra Singh Parmar" at the bottom of the Email body.
            ONLY JSON, no extra text.
            """.trimIndent()
        }
    }

    /**
     * Call Gemini API - CORRECTED VERSION
     */
    private suspend fun callGeminiAPI(prompt: String): EmailContent? {
        return withContext(Dispatchers.IO) {
            try {
                // ⭐ CORRECTED: Use gemini-1.5-flash instead of gemini-2.0-flash-exp
                val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=AIzaSyBGPQuF9rz4Z0N0JF4MM7wxBoEkviqs6tQ"

                Log.d(TAG, "🌐 Calling Gemini API...")
                Log.d(TAG, "📝 Prompt length: ${prompt.length} chars")

                // Create request payload
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))

                    // ⭐ ADD generation config for better JSON output
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("topK", 1)
                        put("topP", 1)
                        put("maxOutputTokens", 256)
                    })
                }

                Log.d(TAG, "📤 Payload: ${payload.toString().take(200)}...")

                val mediaType = "application/json".toMediaType()
                val requestBody = payload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                Log.d(TAG, "📥 Response code: ${response.code}")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "❌ API error: ${response.code}")
                    Log.e(TAG, "❌ Error body: $errorBody")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                Log.d(TAG, "📄 Response: ${body.take(300)}...")

                // Parse response
                val json = JSONObject(body)

                if (!json.has("candidates")) {
                    Log.e(TAG, "❌ No candidates in response")
                    return@withContext null
                }

                val candidates = json.getJSONArray("candidates")

                if (candidates.length() == 0) {
                    Log.e(TAG, "❌ Empty candidates array")
                    return@withContext null
                }

                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")

                Log.d(TAG, "📝 Raw text: $text")

                // Clean and parse JSON from response
                val cleanedText = text
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                Log.d(TAG, "🧹 Cleaned text: $cleanedText")

                try {
                    val emailJson = JSONObject(cleanedText)
                    val subject = emailJson.getString("subject")
                    val emailBody = emailJson.getString("body")

                    Log.d(TAG, "✅ Parsed - Subject: $subject")
                    Log.d(TAG, "✅ Parsed - Body: ${emailBody.take(50)}...")

                    return@withContext EmailContent(subject, emailBody)

                } catch (e: Exception) {
                    Log.e(TAG, "❌ JSON parse error: ${e.message}")

                    // ⭐ FALLBACK: Try to extract subject and body manually
                    val subjectMatch = Regex("\"subject\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                    val bodyMatch = Regex("\"body\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)

                    if (subjectMatch != null && bodyMatch != null) {
                        val subject = subjectMatch.groupValues[1]
                        val emailBody = bodyMatch.groupValues[1]

                        Log.d(TAG, "✅ Manual extraction - Subject: $subject")
                        Log.d(TAG, "✅ Manual extraction - Body: ${emailBody.take(50)}...")

                        return@withContext EmailContent(subject, emailBody)
                    }

                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Gemini API call failed: ${e.message}", e)
                return@withContext null
            }
        }
    }

    /**
     * Data class for email content
     */
    data class EmailContent(
        val subject: String,
        val body: String
    )
}