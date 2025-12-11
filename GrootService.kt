package com.example.groot

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.groot.email.EmailManager
import com.example.groot.email.GeminiEmailAssistant
import com.example.groot.user.EmailPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.example.groot.birthday.BirthdayManager
import android.app.AlarmManager
import android.content.Context

fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length
    val d = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) d[i][0] = i
    for (j in 0..len2) d[0][j] = j

    for (i in 1..len1) {
        for (j in 1..len2) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            d[i][j] = minOf(
                d[i - 1][j] + 1,
                d[i][j - 1] + 1,
                d[i - 1][j - 1] + cost
            )
        }
    }
    return d[len1][len2]
}

class GrootService : Service() {

        private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private lateinit var llmManager: LLMManager
        private lateinit var taskManager: TaskAutomationManager
        private val offlineIntelligence = OfflineIntelligence()
        private lateinit var memoryManager: MemoryManager

        private val conversationStateManager = ConversationStateManager()
        private var personalityManager: PersonalityManager? = null
        private var ttsManager: TTSManager? = null
        // Advanced Managers
        private lateinit var smartActionManager: SmartActionManager
        private lateinit var contactManager: ContactManager
        private lateinit var routineLearning: RoutineLearningManager
        private lateinit var suggestionManager: SuggestionManager

        // VOICE-ONLY Notification Helper (No buttons)
        private lateinit var notificationHelper: NotificationHelper

        private lateinit var locationTracker: LocationTracker
        private lateinit var weatherManager: WeatherManager
        private lateinit var weatherReminderManager: WeatherReminderManager
        private lateinit var calendarManager: CalendarManager
        private lateinit var batteryHelper: BatteryOptimizationHelper
        private lateinit var geofenceHelper: GeofenceHelper
        private lateinit var reminderManager: ReminderManager
        private lateinit var emailManager: EmailManager
        private lateinit var geminiEmailAssistant: GeminiEmailAssistant
        private lateinit var birthdayManager: BirthdayManager
        private val httpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        private lateinit var emailPreferences: EmailPreferences
        //private lateinit var birthdayManager: BirthdayManager

        companion object {
            private const val TAG = "GrootService"
        }
        private var lastDetectedLanguage: Boolean = false

        inner class LocalBinder : Binder() {
            fun getService(): GrootService = this@GrootService
        }

        private val binder = LocalBinder()

        override fun onCreate() {
            super.onCreate()
            llmManager = LLMManager(this)
            taskManager = TaskAutomationManager(this)
            memoryManager = MemoryManager(this)
            smartActionManager = SmartActionManager(this)
            ttsManager = TTSManager(this)
            contactManager = ContactManager(this)
            routineLearning = RoutineLearningManager(this)
            suggestionManager = SuggestionManager(this)

            // VOICE-ONLY Notification Helper
            notificationHelper = NotificationHelper(this)

            locationTracker = LocationTracker(this)
            weatherManager = WeatherManager(this)
            weatherReminderManager = WeatherReminderManager(this)
            calendarManager = CalendarManager(this)
            batteryHelper = BatteryOptimizationHelper(this)
            geofenceHelper = GeofenceHelper(this)
            reminderManager = ReminderManager(this)
            // Initialize email system
            emailManager = EmailManager(this)
            geminiEmailAssistant = GeminiEmailAssistant(this)
            emailPreferences = EmailPreferences(this)
            birthdayManager = BirthdayManager(this)// Initialize birthday system
            serviceScope.launch {
                try {
                    birthdayManager.initialize()
                    Log.d(TAG, "✅ Birthday Manager initialized")
                    birthdayManager.scheduleMidnightBirthdayCheck()
                    checkAlarmStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Birthday Manager initialization failed", e)
                }
            }

            // Setup email credentials (⚠️ USE YOUR REAL GMAIL & APP PASSWORD)
            /*emailManager.setupEmailCredentials(
                email = "mparmar.dev@gmail.com",  // ← Change this
                password = "ovwdeqcabkmeczti"  // ← Change this (NOT regular password!)
            )*/

            // Add contacts
            /*emailManager.addContact("Ram", "rameshwarshinde737@gmail.com")
            emailManager.addContact("Priya", "priya@example.com")
            emailManager.addContact("Boss", "boss@example.com")
            emailManager.addContact("Team Lead", "pratiksha.mahajan@aplitetech.com")
            emailManager.addContact("Team", "msparmar.dev@gmail.com")*/
            // Load credentials from preferences
            if (emailPreferences.isEmailConfigured()) {
                val senderEmail = emailPreferences.getSenderEmail()
                val senderPassword = emailPreferences.getSenderPassword()

                if (senderEmail != null && senderPassword != null) {
                    emailManager.setupEmailCredentials(senderEmail, senderPassword)
                    Log.d(TAG, "✅ Email credentials loaded from preferences")
                }

                // Load contacts
                val contacts = emailPreferences.loadContacts()
                contacts.forEach { (name, email) ->
                    emailManager.addContact(name, email)
                }
                Log.d(TAG, "✅ ${contacts.size} email contacts loaded")
            } else {
                Log.w(TAG, "⚠️ Email not configured - user needs to set up")
            }

            Log.d(TAG, "✅ Email system initialized")

            // Setup timeout callback
            conversationStateManager.onTimeout = { message ->
                Log.w(TAG, "⏰ State timeout: $message")
                // Optionally notify user via some mechanism
            }
            testAPIs()
            Log.d(TAG, "GrootService created")
            serviceScope.launch {
                try {
                    ttsManager?.initialize()
                    Log.d(TAG, "✅ TTS initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ TTS initialization failed: ${e.message}")
                }
            }


            // Start suggestion monitoring
            suggestionManager.startMonitoring()

            // Start location tracking
            //startLocationTracking()

            Log.d(TAG, "GrootService created with VOICE-ONLY approach")

        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            Log.d(TAG, "GrootService started")
            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder = binder

        override fun onDestroy() {
            serviceScope.cancel()
            Log.d(TAG, "GrootService destroyed")
            super.onDestroy()
        }
        /**
         * Set TTSManager and initialize PersonalityManager
         */
        fun setTTSManager(manager: TTSManager) {
            this.ttsManager = manager
            // PersonalityManager doesn't need TTS anymore, just for text enhancements
            this.personalityManager = PersonalityManager(null)
            Log.d(TAG, "TTSManager and PersonalityManager initialized")
        }
        /**
         * Enhance response text based on personality mode
         */
        private fun enhanceResponse(text: String, isHindi: Boolean): String {
            return personalityManager?.getModeSpecificResponse(text, isHindi) ?: text
        }
        private fun matchIntent(command: String, vararg keywords: String): Boolean {
            val lowerCmd = command.lowercase()
            return keywords.any { keyword ->
                val distance = levenshteinDistance(lowerCmd, keyword.lowercase())
                distance <= 2 || lowerCmd.contains(keyword.lowercase())
            }
        }
        private suspend fun fetchNews(query: String = ""): String {
            return withContext(Dispatchers.IO) {
                try {
                    val apiKey = "db0c750ac5e149aabc3b0130f07890a8"
                    val searchQuery = if (query.isNotEmpty()) query else "india"
                    val url = "https://newsapi.org/v2/everything?q=$searchQuery&sortBy=publishedAt&language=en&apiKey=$apiKey"

                    Log.d(TAG, "📰 News URL: $url")

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    Log.d(TAG, "📰 Response Code: ${response.code}")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "📰 Error: ${response.code} - $errorBody")
                        return@withContext "News API Error: ${response.code}"
                    }

                    val body = response.body?.string() ?: return@withContext "Empty response"
                    Log.d(TAG, "📰 Response received: ${body.take(200)}...")

                    val json = org.json.JSONObject(body)
                    val articles = json.getJSONArray("articles")

                    Log.d(TAG, "📰 Articles count: ${articles.length()}")

                    if (articles.length() > 0) {
                        val article = articles.getJSONObject(0)
                        val title = article.getString("title")
                        val description = article.optString("description", "No description")
                        val result = "Top news: $title. $description"
                        Log.d(TAG, "📰 Success: $result")
                        result
                    } else {
                        "No news found."
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "📰 News fetch error: ${e.javaClass.simpleName} - ${e.message}")
                    "News error: ${e.message}"
                }
            }
        }

        private suspend fun fetchJoke(): String {
            return withContext(Dispatchers.IO) {
                try {
                    val url = "https://icanhazdadjoke.com/?format=json"
                    Log.d(TAG, "😂 Joke URL: $url")

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    Log.d(TAG, "😂 Response Code: ${response.code}")

                    if (!response.isSuccessful) {
                        Log.e(TAG, "😂 Error: ${response.code}")
                        return@withContext "Joke API Error: ${response.code}"
                    }

                    val body = response.body?.string() ?: return@withContext "Empty response"
                    Log.d(TAG, "😂 Response: ${body.take(200)}...")

                    val json = org.json.JSONObject(body)
                    val joke = json.getString("joke")
                    Log.d(TAG, "😂 Success: $joke")
                    joke

                } catch (e: Exception) {
                    Log.e(TAG, "😂 Joke error: ${e.javaClass.simpleName} - ${e.message}")
                    "Joke error: ${e.message}"
                }
            }
        }

        private suspend fun fetchSong(songName: String): String {
            return try {
                // Simple fallback - you can integrate Genius API later
                val geniusApiKey = "YOUR_GENIUS_API_KEY" // Optional

                // For now, return a formatted response
                "Now playing information for $songName. To search songs, use music streaming apps."
            } catch (e: Exception) {
                "Couldn't fetch song information."
            }
        }

        private suspend fun fetchStory(): String {
            return try {
                // Option 1: Simple hardcoded stories
                val stories = listOf(
                    "Once upon a time, there was a smart assistant who helped everyone with their daily tasks.",
                    "In a land far away, a mysterious AI learned to understand human emotions and became everyone's best friend.",
                    "A story about perseverance: A young boy dreamed of becoming a programmer, and after years of hard work, he did."
                )
                stories.random()
            } catch (e: Exception) {
                "Couldn't fetch a story right now."
            }
        }

        private fun cleanMarkdown(text: String): String {
            return text
                // Remove bold markdown ** **
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                // Remove italic markdown * *
                .replace(Regex("\\*(.*?)\\*"), "$1")
                // Remove heading markdown #
                .replace(Regex("#+\\s"), "")
                // Remove code markdown ` `
                .replace(Regex("`(.*?)`"), "$1")
                // Remove links [text](url)
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
                // Remove line breaks and extra spaces
                .replace(Regex("\\n\\n+"), "\n")
                .trim()
        }

        private suspend fun generalQuery(query: String): String {
            return withContext(Dispatchers.IO) {
                try {
                    val geminiKey = "AIzaSyBGPQuF9rz4Z0N0JF4MM7wxBoEkviqs6tQ"

                    // ⭐ UPDATED: Use v1 instead of v1beta and gemini-2.0-flash
                    val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=$geminiKey"

                    Log.d(TAG, "🤖 Gemini URL: $url")
                    Log.d(TAG, "🤖 Query: $query")

                    val payload = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().put(
                            org.json.JSONObject().put("parts", org.json.JSONArray().put(
                                org.json.JSONObject().put("text", query)
                            ))
                        ))
                    }

                    Log.d(TAG, "🤖 Payload: ${payload.toString().take(200)}...")

                    val mediaType = "application/json".toMediaType()
                    val requestBody = payload.toString().toRequestBody(mediaType)

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .header("Content-Type", "application/json")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    Log.d(TAG, "🤖 Response Code: ${response.code}")

                    // Handle 503 (overloaded) - try fallback
                    if (response.code == 503) {
                        Log.w(TAG, "🤖 API overloaded (503), trying fallback...")
                        return@withContext "Gemini is busy right now. Please try again in a moment."
                    }

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "🤖 Error: ${response.code} - $errorBody")
                        return@withContext "Gemini API Error: ${response.code}"
                    }

                    val body = response.body?.string() ?: return@withContext "Empty response"
                    Log.d(TAG, "🤖 Response: ${body.take(300)}...")

                    val json = org.json.JSONObject(body)

                    if (!json.has("candidates")) {
                        Log.e(TAG, "🤖 No candidates in response")
                        return@withContext "No response from AI"
                    }

                    val candidates = json.getJSONArray("candidates")
                    if (candidates.length() == 0) {
                        Log.e(TAG, "🤖 Empty candidates array")
                        return@withContext "Empty Gemini response"
                    }

                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")

                    // ⭐ CLEAN MARKDOWN
                    var inhancedText = cleanMarkdown(text)

                    Log.d(TAG, "🤖 Success: ${text.take(200)}...")
                    inhancedText

                } catch (e: Exception) {
                    Log.e(TAG, "🤖 Gemini error: ${e.javaClass.simpleName} - ${e.message}")
                    "Gemini error: ${e.message}"
                }
            }
        }

        //generalQueryWithRetry for 503 errors
        private suspend fun generalQueryWithRetry(query: String, maxRetries: Int = 2): String {
            return withContext(Dispatchers.IO) {
                repeat(maxRetries) { attempt ->
                    try {
                        val result = generalQuery(query)
                        if (!result.contains("busy", ignoreCase = true)) {
                            return@withContext result
                        }
                        // If API is busy, wait and retry
                        if (attempt < maxRetries - 1) {
                            Log.d(TAG, "Retrying Gemini API... (attempt ${attempt + 2})")
                            delay(2000) // Wait 2 seconds before retry
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Retry failed: ${e.message}")
                    }
                }
                return@withContext "API is temporarily busy. Please try again shortly."
            }
        }

        // Alternative: If gemini-2.0-flash doesn't work, try this:
        private suspend fun generalQueryAlternative(query: String): String {
            return withContext(Dispatchers.IO) {
                try {
                    val geminiKey = "AIzaSyBGPQuF9rz4Z0N0JF4MM7wxBoEkviqs6tQ"

                    // Alternative URL
                    val url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$geminiKey"

                    Log.d(TAG, "🤖 Gemini URL (Alternative): $url")

                    val payload = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().put(
                            org.json.JSONObject().put("parts", org.json.JSONArray().put(
                                org.json.JSONObject().put("text", query)
                            ))
                        ))
                    }

                    val mediaType = "application/json".toMediaType()
                    val requestBody = payload.toString().toRequestBody(mediaType)

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .header("Content-Type", "application/json")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    Log.d(TAG, "🤖 Response Code: ${response.code}")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "🤖 Error: ${response.code} - $errorBody")
                        return@withContext "Gemini API Error: ${response.code}"
                    }

                    val body = response.body?.string() ?: return@withContext "Empty response"
                    val json = org.json.JSONObject(body)

                    val candidates = json.getJSONArray("candidates")
                    if (candidates.length() == 0) {
                        return@withContext "Empty Gemini response"
                    }

                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")

                    Log.d(TAG, "🤖 Success (Alternative): ${text.take(200)}...")
                    text

                } catch (e: Exception) {
                    Log.e(TAG, "🤖 Gemini error (Alternative): ${e.javaClass.simpleName} - ${e.message}")
                    "Gemini error: ${e.message}"
                }
            }
        }

        private fun extractSearchTerm(command: String, keyword: String): String {
            val regex = Regex("(?:$keyword|about|के बारे में|about)\\s+(.+?)(?:\\s+in|\\s+for|$)", RegexOption.IGNORE_CASE)
            return regex.find(command)?.groupValues?.get(1)?.trim() ?: ""
        }
        fun testAPIs() {
            serviceScope.launch {
                Log.d(TAG, "=== STARTING API TESTS ===")

                Log.d(TAG, "TEST 1: News API")
                val news = fetchNews("india")
                Log.d(TAG, "NEWS RESULT: $news")

                Log.d(TAG, "TEST 2: Joke API")
                val joke = fetchJoke()
                Log.d(TAG, "JOKE RESULT: $joke")

                Log.d(TAG, "TEST 3: Gemini API")
                val gemini = generalQuery("Hello, who are you?")
                Log.d(TAG, "GEMINI RESULT: $gemini")

                Log.d(TAG, "=== TESTS COMPLETED ===")
            }
        }

        /**
         * Main command processing with TaskAutomationManager integration
         */
        @RequiresApi(Build.VERSION_CODES.O)
        fun processCommand(command: String, callback: (String, String, String, Boolean, String) -> Unit) {

            Log.d(TAG, "🎤 Received command: $command")
            serviceScope.launch(Dispatchers.IO) {

                try {
                    lastDetectedLanguage = LanguageDetector.isHindiIntent(command)
                    // STEP 0: Check if we have pending state (disambiguation/confirmation)
                    if (conversationStateManager.hasPendingAction()) {
                        handleStatefulCommand(command, callback)
                        return@launch
                    }

                    // STEP 1: Check memory queries
                    val memoryResponse = checkMemoryQuery(command)
                    if (memoryResponse != null) {
                        Log.i(TAG, "✅ Answered from memory: $memoryResponse")
                        val emotion = detectEmotion(command, "none")
                        withContext(Dispatchers.Main) {
                            callback(memoryResponse, "none", "", false, emotion)
                        }
                        return@launch
                    }

                    // STEP 2: Try offline intelligence
                    Log.d(TAG, "🔍 Trying offline intelligence...$command")
                    val offlineResponse = offlineIntelligence.handleOffline(command, taskManager)

                    if (offlineResponse.handled) {
                        Log.i(TAG, "✅ Handled offline: ${offlineResponse.reply}")
                        val emotion = detectEmotion(command, offlineResponse.action)
                        val enhancedReply = enhanceResponse(offlineResponse.reply, lastDetectedLanguage)
                        // Handle prompt_continue action
                        if (offlineResponse.action == "prompt_continue") {
                            callback(offlineResponse.reply, "none", "", true, "friendly")
                            return@launch
                        }
                        if (offlineResponse.action == "greeting_with_personality") {
                            val personalizedGreeting = personalityManager?.getGreeting(
                                offlineResponse.reply,
                                lastDetectedLanguage
                            ) ?: offlineResponse.reply

                            callback(personalizedGreeting, "none", "", true,"excited")
                            return@launch
                        }
                        // Handle weather queries
                        if (offlineResponse.action == "get_weather") {
                            handleWeatherQuery(offlineResponse.target, callback)
                            return@launch
                        }

                        // City-specific weather
                        if (offlineResponse.action == "weather_city") {
                            handleCityWeather(offlineResponse.target, callback)
                            return@launch
                        }

                        // Weather forecast
                        if (offlineResponse.action == "weather_forecast") {
                            handleWeatherForecast(offlineResponse.target, callback)
                            return@launch
                        }

                        // Weather alerts
                        if (offlineResponse.action == "weather_alerts") {
                            handleWeatherAlerts(callback)
                            return@launch
                        }

                        // Check tomorrow's weather
                        if (offlineResponse.action == "check_tomorrow_weather") {
                            checkTomorrowWeather(offlineResponse.target, callback)
                            return@launch
                        }

                        // Weather suggestions
                        if (offlineResponse.action == "weather_suggestion") {
                            handleWeatherSuggestion(offlineResponse.target, callback)
                            return@launch
                        }

                        // Set umbrella reminder
                        if (offlineResponse.action == "set_umbrella_reminder") {
                            handleUmbrellaReminder(offlineResponse.target, callback)
                            return@launch
                        }

                        // Show weather reminders
                        if (offlineResponse.action == "show_weather_reminders") {
                            handleShowWeatherReminders(callback)
                            return@launch
                        }

                        // Cancel weather reminders
                        if (offlineResponse.action == "cancel_weather_reminders") {
                            handleCancelWeatherReminders(callback)
                            return@launch
                        }
                        // ✅ END OF WEATHER BLOCK
                        // ==================== REMINDER ACTIONS ====================

                        if (offlineResponse.action == "set_reminder") {
                            Log.d(TAG,"set_reminder handled")
                            handleSetReminder(offlineResponse.target, callback)
                            return@launch
                        }

                        if (offlineResponse.action == "show_reminders") {
                            handleShowReminders(callback)
                            return@launch
                        }

                        if (offlineResponse.action == "cancel_reminders") {
                            handleCancelReminders(callback)
                            return@launch
                        }
                        // ==================== EMAIL ACTIONS ====================

                        if (offlineResponse.action == "ask_email_recipient") {
                            // User said just "email" - ask for recipient
                            Log.d(TAG, "📧 Asking for email recipient")
                            callback(offlineResponse.reply, "none", "", true, "friendly") // autoRestart = true

                            // Set state to collect recipient
                            conversationStateManager.setState(
                                action = "collect_email_recipient",
                                originalIntent = "send_email"
                            )
                            return@launch
                        }

                        if (offlineResponse.action == "send_email") {
                            handleEmailCommand(offlineResponse.target, callback)
                            return@launch
                        }

                        // Handle personality mode changes
                        when (offlineResponse.action) {
                            "set_mode_funny" -> {
                                personalityManager?.setMode(PersonalityManager.PersonalityMode.FUNNY)
                                callback(offlineResponse.reply, "none", "", false,"happy")
                                serviceScope.launch {
                                    delay(1000)
                                    promptContinue(callback)
                                }
                                return@launch
                            }

                            "set_mode_professional" -> {
                                personalityManager?.setMode(PersonalityManager.PersonalityMode.PROFESSIONAL)
                                callback(offlineResponse.reply, "none", "", false, "neutral")
                                serviceScope.launch {
                                    delay(1000)
                                    promptContinue(callback)
                                }
                                return@launch
                            }

                            "set_mode_friendly" -> {
                                personalityManager?.setMode(PersonalityManager.PersonalityMode.FRIENDLY)
                                callback(offlineResponse.reply, "none", "", false, "friendly")
                                serviceScope.launch {
                                    delay(1000)
                                    promptContinue(callback)
                                }
                                return@launch
                            }

                            "set_mode_serious" -> {
                                personalityManager?.setMode(PersonalityManager.PersonalityMode.SERIOUS)
                                callback(offlineResponse.reply, "none", "", false, "urgent")
                                serviceScope.launch {
                                    delay(1000)
                                    promptContinue(callback)
                                }
                                return@launch
                            }
                        }

                        // Handle context-aware commands
                        if (offlineResponse.action == "use_context") {
                            val recentContact = conversationStateManager.getRecentContact()

                            if (recentContact != null) {
                                val contextualCommand = command
                                    .replace(Regex("usko|unko|उसको|उनको", RegexOption.IGNORE_CASE), recentContact)

                                Log.d(TAG, "Using context: '$command' → '$contextualCommand'")
                                processCommand(contextualCommand, callback)
                                return@launch
                            } else {
                                val reply = if (LanguageDetector.isHindiIntent(command)) {
                                    "किसके बारे में बता रहे हैं आप? sir"
                                } else {
                                    "sir, Who are you referring to?"
                                }
                                callback(reply, "none", "", true, "excited")
                                return@launch
                            }
                        }

                        // Handle pending SMS message collection
                        if (offlineResponse.action == "pending_sms_message") {
                            val contactName = offlineResponse.target

                            // Set state to wait for message
                            conversationStateManager.setState(
                                action = "collect_sms_message",
                                originalIntent = "sms"
                            )
                            conversationStateManager.getState().smsRecipient = contactName

                            // Ask for message and auto-restart mic
                            callback(offlineResponse.reply, "none", "", true, "neutral")
                            return@launch
                        }

                        // Check if action needs disambiguation
                        if (offlineResponse.action == "call" || offlineResponse.action == "get_number") {
                            val contactName = offlineResponse.target
                            val searchResult = taskManager.searchContactWithDisambiguation(contactName)

                            if (!searchResult.isSingleMatch && searchResult.multipleMatches != null && searchResult.multipleMatches.isNotEmpty()) {
                                // Multiple matches - set state and ask user
                                handleDisambiguation(
                                    matches = searchResult.multipleMatches,
                                    originalIntent = offlineResponse.action,
                                    callback = callback
                                )
                                return@launch
                            }
                        }

                        // NEW: Handle SMS with disambiguation
                        if (offlineResponse.action == "sms") {
                            val parts = offlineResponse.target.split(":", limit = 2)
                            if (parts.size == 2) {
                                val contactName = parts[0].trim()
                                val messageText = parts[1].trim()

                                val searchResult = taskManager.searchContactWithDisambiguation(contactName)

                                if (!searchResult.isSingleMatch && searchResult.multipleMatches != null && searchResult.multipleMatches.isNotEmpty()) {
                                    // Multiple contacts - need disambiguation
                                    handleSMSDisambiguation(
                                        matches = searchResult.multipleMatches,
                                        messageText = messageText,
                                        callback = callback
                                    )
                                    return@launch
                                } else if (searchResult.isSingleMatch && searchResult.singleContact != null) {
                                    // Single contact - confirm before sending
                                    handleSMSConfirmation(
                                        contactName = contactName,
                                        phoneNumber = searchResult.singleContact,
                                        messageText = messageText,
                                        callback = callback
                                    )
                                    return@launch
                                }
                            }
                        }

                        // Execute action
                        if (offlineResponse.action != "none") {
                            taskManager.executeAction(
                                offlineResponse.action,
                                offlineResponse.target,
                                confidence = 1.0
                            )
                        }

                        callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target, false, emotion)
                        return@launch
                    }

                    // STEP 3: Save preferences
                    saveUserPreferences(command)


                    // ⭐ STEP 4: LIVE DATA & GENERAL QUERIES (BEFORE SERVER) ⭐
                    // STEP 4: Try live data sources
                    Log.d(TAG, "📡 Trying live data sources...")

                    when {
                        matchIntent(command, "news") -> {
                            val searchTerm = extractSearchTerm(command, "news")
                            val news = fetchNews(searchTerm)
                            withContext(Dispatchers.Main) {
                                callback(news, "none", "", false, "neutral")
                            }
                            return@launch
                        }
                        matchIntent(command, "joke", "मजाक") -> {
                            val joke = fetchJoke()
                            withContext(Dispatchers.Main) {
                                callback(joke, "none", "", false, "happy")
                            }
                            return@launch
                        }
                        matchIntent(command, "story", "कहानी") -> {
                            val story = fetchStory()
                            withContext(Dispatchers.Main) {
                                callback(story, "none", "", false, "friendly")
                            }
                            return@launch
                        }
                        matchIntent(command, "song", "music", "गाना") -> {
                            val songName = extractSearchTerm(command, "song")
                            val songInfo = fetchSong(songName)
                            withContext(Dispatchers.Main) {
                                callback(songInfo, "none", "", false, "happy")
                            }
                            return@launch
                        }
                        // Check for birthday-related commands
                         matchIntent(command, "birthday", "जन्मदिन")-> {
                            //handleBirthdayCommand(command, callback)
                            return@launch
                        }
                    }

                    // STEP 4: Try server
                    Log.d(TAG, "🌐 Trying server...")
                    val response = llmManager.generateResponse(command)
                    val json = JSONObject(response)
                    val reply = json.optString("reply", "")
                    val action = json.optString("action", "none")
                    val target = json.optString("target", "")

                    if (action != "none") {
                        taskManager.executeAction(action, target, confidence = 1.0)
                    }

                    val emotion = detectEmotion(command, action)
                    if (reply.isNotEmpty() && !reply.contains("sorry", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            callback(reply, action, target, false, emotion)
                        }
                    } else {
                        // STEP 6: Gemini fallback
                        Log.d(TAG, "🤖 Trying Gemini for general answer...")
                        val answer = generalQueryWithRetry(command)
                        withContext(Dispatchers.Main) {
                            callback(answer, "none", "", false, "neutral")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error: ${e.message}", e)
                    val offlineResponse = offlineIntelligence.handleOffline(command, taskManager)
                    if (offlineResponse.handled) {
                        callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target, false, "neutral")
                    } else {
                        callback("I'm sorry, I couldn't process that.", "none", "", false, "sad")
                    }
                }
            }
        }
        /**
         * Detect emotion based on command context
         */
        private fun detectEmotion(command: String, action: String): String {
            val lowerCmd = command.lowercase()

            return when {
                // Happy/Success
                lowerCmd.contains("thank") ||
                        lowerCmd.contains("धन्यवाद") ||
                        lowerCmd.contains("great") -> "happy"

                // Excited
                lowerCmd.contains("yay") ||
                        lowerCmd.contains("awesome") -> "excited"

                // Urgent/Emergency
                action == "emergency_protocol" ||
                        lowerCmd.contains("emergency") ||
                        lowerCmd.contains("urgent") -> "urgent"

                // Calm
                lowerCmd.contains("meditate") ||
                        lowerCmd.contains("relax") ||
                        lowerCmd.contains("sleep") -> "calm"

                // Friendly (greetings)
                lowerCmd.contains("hello") ||
                        lowerCmd.contains("hi") ||
                        lowerCmd.contains("namaste") -> "friendly"

                // Sad
                lowerCmd.contains("sad") ||
                        lowerCmd.contains("worried") -> "sad"

                else -> "neutral"
            }
        }

        /**
         * Ask user if they need more help and restart mic
         */

        private fun promptContinue(callback: (String, String, String, Boolean, String) -> Unit) {
            val message = ResponseVariations.getRandomPrompt(lastDetectedLanguage)
            callback(message, "none", "", true, "friendly")
        }


        /**
         * Handle commands when there's pending state (disambiguation/confirmation)
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun handleStatefulCommand(command: String, callback: (String, String, String, Boolean, String) -> Unit) {
            val state = conversationStateManager.getState()
            val isHindi = LanguageDetector.isHindiIntent(command)

            Log.d(TAG, "🔄 Handling stateful command. Current action: ${state.pendingAction}")

            when (state.pendingAction) {
                "disambiguate_contact" -> {
                    val isHindi = LanguageDetector.isHindiIntent(command)
                    // Check if user wants next page
                    if (conversationStateManager.isNextPageRequest(command)) {
                        val nextPageText = conversationStateManager.getNextPage(isHindi)
                        if (nextPageText != null) {

                            callback(nextPageText, "none", "", true, "neutral") // autoRestart = true
                            return
                        }
                    }
                    // Try to match user's selection
                    val matched = conversationStateManager.matchDisambiguation(command)

                    if (matched != null) {
                        Log.i(TAG, "✅ Disambiguation matched: ${matched.displayName}")

                        // Execute original intent
                        when (state.originalIntent) {
                            "get_number" -> {
                                val reply = if (isHindi)
                                    "${matched.displayName} का नंबर है: ${matched.number}"
                                else
                                    "${matched.displayName}'s number is: ${matched.number}"

                                // Ask for call confirmation
                                conversationStateManager.setState(
                                    action = "confirm_call",
                                    selectedContact = matched.displayName,
                                    selectedNumber = matched.number,
                                    originalIntent = "call"
                                )

                                val confirmPrompt = if (isHindi) "\nइनको कॉल भी करूं?"
                                else "\nShall I call them?"

                                callback(reply + confirmPrompt, "none", "", true, "excited") // autoRestart = true
                                serviceScope.launch {
                                    delay(15000)
                                    promptContinue(callback)
                                }
                            }
                            "call" -> {
                                conversationStateManager.setState(
                                    action = "confirm_call",
                                    selectedContact = matched.displayName,
                                    selectedNumber = matched.number,
                                    originalIntent = "call"
                                )
                                // Direct call after disambiguation
                                val confirmMsg = if (isHindi) {
                                    "${matched.displayName} को कॉल करूं?"
                                } else {
                                    "Should I call ${matched.displayName}?"
                                }

                                conversationStateManager.clearState()
                                callback(confirmMsg, "call", matched.number, false, "happy")

                                serviceScope.launch {
                                    taskManager.executeAction("call", matched.number, 1.0)
                                }
                            }
                        }
                    } else {
                        // No match - increment failure
                        conversationStateManager.incrementFailure()

                        if (state.failureCount >= 2) {
                            val reply = if (isHindi)
                                "मुझे समझने में दिक्कत हो रही है। कृपया पूरा नाम बोलें या फिर से try करें।"
                            else
                                "I'm having trouble understanding. Please say the full name clearly."

                            conversationStateManager.clearState()
                            callback(reply, "none", "", false, "sad")
                        } else {
                            val reply = if (isHindi)
                                "मैंने सही से नहीं सुना। कृपया फिर से बोलें किसे select करना है?"
                            else
                                "Sorry, I didn't catch that. Please say again which one to select?"

                            callback(reply, "none", "", true, "sad") // autoRestart = true
                        }
                    }
                }

                "confirm_call" -> {
                    val state = conversationStateManager.getState()
                    val isHindi = LanguageDetector.isHindiIntent(command)

                    if (conversationStateManager.isPositiveResponse(command)) {
                        val ack = ResponseVariations.getPositiveAck(isHindi)
                        val action = if (isHindi) "कॉल कर रहा हूं" else "calling"
                        val reply = "$ack,  $action ${state.selectedContact}"

                        val enhancedReply = enhanceResponse(reply, isHindi)

                        conversationStateManager.recordAction("call", state.selectedContact ?: "")
                        conversationStateManager.clearState()

                        callback(enhancedReply, "call", state.selectedNumber ?: "", false, "happy")

                        // Register callback for when call ends
                        taskManager.setCallEndCallback {
                            serviceScope.launch {
                                delay(10000)
                                val completionMsg = if (isHindi) "कॉल समाप्त हो गई।"
                                else "Call ended."
                                callback(completionMsg, "none", "", false, "neutral")

                                delay(5000)
                                promptContinue(callback)
                            }
                        }

                        // Start monitoring call state
                        taskManager.startCallMonitoring()

                        // Initiate call
                        serviceScope.launch {
                            delay(5000)
                            taskManager.executeAction("call", state.selectedNumber ?: "", 1.0)
                        }

                    } else if (conversationStateManager.isNegativeResponse(command)) {
                        val reply = if (isHindi) "ठीक है, कॉल नहीं कर रहा।" else "Okay, not calling."
                        conversationStateManager.clearState()
                        callback(reply, "none", "", false, "neutral")
                    } else {
                        val reply = ResponseVariations.getConfusionResponse(isHindi)
                        callback(reply, "none", "", true, "neutral")
                    }
                }
                "disambiguate_sms" -> {
                    val isHindi = LanguageDetector.isHindiIntent(command)
                    // Check for next page
                    if (conversationStateManager.isNextPageRequest(command)) {
                        val nextPageText = conversationStateManager.getNextPage(isHindi)
                        if (nextPageText != null) {
                            callback(nextPageText, "none", "", true,"")
                            return
                        }
                    }

                    // Try to match user's selection
                    val matched = conversationStateManager.matchDisambiguation(command)
                    val state = conversationStateManager.getState()

                    if (matched != null && state.smsMessage != null) {
                        Log.i(TAG, "✅ SMS disambiguation matched: ${matched.displayName}")

                        // Now confirm before sending
                        handleSMSConfirmation(
                            contactName = matched.displayName,
                            phoneNumber = matched.number,
                            messageText = state.smsMessage!!,
                            callback = callback
                        )
                    } else {
                        // No match - increment failure
                        conversationStateManager.incrementFailure()

                        if (state.failureCount >= 2) {
                            val reply = ResponseVariations.getConfusionResponse(isHindi) +
                                    if (isHindi) " कृपया फिर से कोशिश करें।"
                                    else " Please try again."
                            conversationStateManager.clearState()
                            callback(reply, "none", "", false,"neutral")
                        } else {
                            val reply = ResponseVariations.getConfusionResponse(isHindi)
                            callback(reply, "none", "", true ,"neutral")
                        }
                    }
                }

                "confirm_sms" -> {
                    val state = conversationStateManager.getState()
                    val isHindi = LanguageDetector.isHindiIntent(command)

                    if (conversationStateManager.isPositiveResponse(command)) {
                        val ack = ResponseVariations.getPositiveAck(isHindi)
                        val action = if (isHindi) "मैसेज भेज रहा हूं" else "sending message"
                        val reply = if (isHindi) "$ack, ${state.selectedContact} को $action" else "$ack, $action to ${state.selectedContact}"

                        val enhancedReply = enhanceResponse(reply, isHindi)
                        conversationStateManager.recordAction("sms", state.selectedContact ?: "")

                        val target = "${state.selectedContact}:${state.smsMessage}"
                        conversationStateManager.clearState()
                        callback(enhancedReply, "sms", target, false, "happy")

                        // Register SMS status callback
                        taskManager.setSMSStatusCallback { success, statusMessage ->
                            serviceScope.launch {
                                val statusReply = if (success) {
                                    if (isHindi) "मैसेज सफलतापूर्वक भेज दिया गया।"
                                    else "Message sent successfully."
                                } else {
                                    if (isHindi) "मैसेज भेजने में विफल: $statusMessage"
                                    else "Failed to send message: $statusMessage"
                                }

                                callback(statusReply, "none", "", false, if (success) "happy" else "sad")
                                delay(1500)
                                promptContinue(callback)
                            }
                        }

                        // Send SMS
                        serviceScope.launch {
                            val parts = target.split(":", limit = 2)
                            if (parts.size == 2) {
                                val number = taskManager.searchContactByName(parts[0].lowercase())
                                if (number != null) {
                                    taskManager.executeAction("sms", "$number:${parts[1]}", 1.0)
                                }else {
                                    callback(if (isHindi) "संपर्क नहीं मिला" else "Contact not found", "none", "", false, "neutral")
                                }
                            }
                        }
                    } else if (conversationStateManager.isNegativeResponse(command)) {
                        val reply = if (isHindi) "ठीक है, मैसेज नहीं भेज रहा।"
                        else "Okay, not sending the message."

                        conversationStateManager.clearState()
                        callback(reply, "none", "", false, "neutral")
                    }else {
                        val reply = ResponseVariations.getConfusionResponse(isHindi)
                        callback(reply, "none", "", true, "neutral")
                    }
                    /*else {
                        val reply = if (isHindi) "मैंने सही से नहीं सुना। मैसेज भेजूं? हां या नहीं बोलें।"
                        else "I didn't understand. Should I send the message? Say yes or no."

                        callback(reply, "none", "", true)
                    }*/

                }

                "collect_sms_message" -> {
                    val state = conversationStateManager.getState()
                    val recipient = state.smsRecipient
                    val isHindi = LanguageDetector.isHindiIntent(command)
                    if (recipient != null && command.trim().isNotEmpty()) {
                        Log.i(TAG, "📱 SMS message collected: $command")

                        // Now we have both contact and message
                        val searchResult = taskManager.searchContactWithDisambiguation(recipient.lowercase())

                        if (!searchResult.isSingleMatch && searchResult.multipleMatches != null && searchResult.multipleMatches.isNotEmpty()) {
                            // Multiple contacts - need disambiguation
                            handleSMSDisambiguation(
                                matches = searchResult.multipleMatches,
                                messageText = command.trim(),
                                callback = callback
                            )
                        } else if (searchResult.isSingleMatch && searchResult.singleContact != null) {
                            // Single contact - confirm before sending
                            handleSMSConfirmation(
                                contactName = recipient,
                                phoneNumber = searchResult.singleContact,
                                messageText = command.trim(),
                                callback = callback
                            )
                        } else {
                            // Contact not found
                            val reply = if (isHindi) "$recipient का संपर्क नहीं मिला"
                            else "Contact $recipient not found"
                            conversationStateManager.clearState()
                            callback(reply, "none", "", false, "neutral")
                        }
                    } else {
                        // Invalid message
                        val reply = if (isHindi) "मैंने कुछ नहीं सुना। क्या मैसेज भेजना है?"
                        else "I didn't hear anything. What message do you want to send?"
                        callback(reply, "none", "", true, "neutral")
                    }
                }
                "collect_email_content" -> {
                    val state = conversationStateManager.getState()
                    val recipientName = state.emailRecipient
                    val recipientEmail = state.emailRecipientEmail
                    val scheduleTime = state.emailScheduleTime
                    val isHindi = LanguageDetector.isHindiIntent(command)

                    if (recipientName != null && recipientEmail != null && command.trim().isNotEmpty()) {
                        Log.i(TAG, "📧 Email content collected: ${command.take(50)}...")

                        // Show processing message
                        val processingMsg = if (isHindi) {
                            "एक सेकंड sir, मैं email तैयार कर रहा हूं..."
                        } else {
                            "One moment sir, preparing your email..."
                        }

                        // ⭐ SPEAK processing message FIRST
                        callback(processingMsg, "none", "", false, "neutral")

                        // ⭐ ADD DELAY - Wait for TTS to complete
                        serviceScope.launch {
                            //delay(3000) // 3 seconds delay
                            delay(calculateSpeechDelay(processingMsg))

                            // Now process with Gemini
                            try {
                                val result = geminiEmailAssistant.processEmailContent(
                                    userSpeech = command,
                                    recipientName = recipientName,
                                    isHindi = isHindi
                                )

                                if (result != null) {
                                    // ⭐ ADD SMALL DELAY before confirmation
                                    delay(1000)

                                    // Show confirmation
                                    showEmailConfirmationFromService(
                                        recipientName = recipientName,
                                        recipientEmail = recipientEmail,
                                        subject = result.subject,
                                        body = result.body,
                                        scheduleTime = scheduleTime,
                                        isHindi = isHindi,
                                        callback = callback
                                    )
                                } else {
                                    // Gemini failed, use raw text
                                    val fallbackSubject = if (isHindi) "संदेश" else "Message"

                                    //delay(1000)
                                    delay(calculateSpeechDelay(fallbackSubject))

                                    showEmailConfirmationFromService(
                                        recipientName = recipientName,
                                        recipientEmail = recipientEmail,
                                        subject = fallbackSubject,
                                        body = command,
                                        scheduleTime = scheduleTime,
                                        isHindi = isHindi,
                                        callback = callback
                                    )
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Gemini processing failed", e)

                                val errorMsg = if (isHindi) {
                                    "Email तैयार करने में समस्या हुई। फिर से try करें?"
                                } else {
                                    "Had trouble preparing email. Try again?"
                                }

                                delay(1000)

                                conversationStateManager.clearState()
                                callback(errorMsg, "none", "", true, "sad")
                            }
                        }

                    } else {
                        // Invalid input
                        val reply = if (isHindi) {
                            "मैंने कुछ नहीं सुना sir। क्या mail भेजना है?"
                        } else {
                            "sir, I didn't hear anything. What should I send?"
                        }

                        callback(reply, "none", "", true, "neutral")
                    }
                }

                "collect_email_recipient" -> {
                    val isHindi = LanguageDetector.isHindiIntent(command)

                    if (command.trim().isNotEmpty()) {
                        Log.i(TAG, "📧 Recipient collected: $command")

                        // Extract just the name (remove extra words)
                        val recipientName = command.trim()
                            .replaceFirst("(?i)^(?:to|ko|को)\\s+".toRegex(), "")
                            .trim()

                        // Check if contact exists
                        val recipientEmail = emailManager.getContactEmail(recipientName)

                        if (recipientEmail != null) {
                            // Contact found - now ask for email content
                            conversationStateManager.setState(
                                action = "collect_email_content",
                                originalIntent = "send_email"
                            )

                            conversationStateManager.getState().apply {
                                this.emailRecipient = recipientName
                                this.emailRecipientEmail = recipientEmail
                            }

                            val askContentMsg = if (isHindi) {
                                "$recipientName को क्या email भेजना है sir?"
                            } else {
                                "sir, What email should I send to $recipientName?"
                            }

                            Log.d(TAG, "📧 Asking for email content")
                            callback(askContentMsg, "none", "", true, "friendly") // autoRestart = true

                        } else {
                            // Contact not found
                            val errorMsg = if (isHindi) {
                                "$recipientName का email नहीं मिला sir। किसी और को भेजें?"
                            } else {
                                "sir, I don't have email for $recipientName. Try someone else?"
                            }

                            conversationStateManager.clearState()
                            callback(errorMsg, "none", "", true, "sad")
                        }

                    } else {
                        // Empty input
                        val reply = if (isHindi) {
                            "मैंने कुछ नहीं सुना sir। किसे email भेजना है?"
                        } else {
                            "sir, I didn't hear anything. Who do you want to email?"
                        }

                        callback(reply, "none", "", true, "neutral")
                    }
                }

                "confirm_email_send" -> {
                    val state = conversationStateManager.getState()
                    val isHindi = LanguageDetector.isHindiIntent(command)

                    if (conversationStateManager.isPositiveResponse(command)) {
                        // User confirmed, send email
                        val ack = ResponseVariations.getPositiveAck(isHindi)
                        val sendingMsg = if (isHindi) {
                            "$ack, email भेज रहा हूं ${state.emailRecipient} को"
                        } else {
                            "$ack, sending email to ${state.emailRecipient}"
                        }

                        // ⭐ SPEAK sending message FIRST
                        callback(sendingMsg, "none", "", false, "happy")

                        // Send email in background
                        serviceScope.launch {
                            // ⭐ WAIT for "sending email" TTS to complete
                            //delay(3000) // 3 seconds
                            delay(calculateSpeechDelay(sendingMsg))
                            try {
                                val success = emailManager.sendEmail(
                                    to = state.emailRecipientEmail!!,
                                    subject = state.emailSubject!!,
                                    body = state.emailBody!!
                                )

                                // ⭐ WAIT for email sending
                                delay(1000) // 2 seconds

                                val resultMsg = if (success) {
                                    if (isHindi) " Email सफलतापूर्वक भेज दिया गया sir।"
                                    else " sir, Email sent successfully."
                                } else {
                                    if (isHindi) "❌ Email भेजने में समस्या हुई sir।"
                                    else "❌ sir, Had trouble sending email."
                                }

                                conversationStateManager.clearState()

                                // ⭐ SPEAK success/failure message
                                callback(resultMsg, "none", "", false, if (success) "happy" else "sad")

                                // ⭐ WAIT for result message to complete
                                //delay(4000) // 4 seconds (longer for success message)
                                delay(calculateSpeechDelay(resultMsg))
                                // ⭐ NOW prompt for next action
                                promptContinue(callback)

                            } catch (e: Exception) {
                                Log.e(TAG, "Email send failed", e)
                                conversationStateManager.clearState()

                                val errorMsg = if (isHindi) "Email भेजने में error आया।"
                                else "Email sending error occurred."

                                callback(errorMsg, "none", "", false, "sad")

                                delay(3000)
                                promptContinue(callback)
                            }
                        }

                    } else if (conversationStateManager.isNegativeResponse(command)) {
                        val reply = if (isHindi) {
                            "ठीक है sir, email नहीं भेज रहा।"
                        } else {
                            "Okay sir, not sending the email."
                        }

                        conversationStateManager.clearState()
                        callback(reply, "none", "", false, "neutral")

                        // ⭐ ADD DELAY before prompt
                        serviceScope.launch {
                            delay(2500)
                            promptContinue(callback)
                        }

                    } else {
                        val reply = ResponseVariations.getConfusionResponse(isHindi)
                        callback(reply, "none", "", true, "neutral")
                    }
                }

                else -> {
                    Log.w(TAG, "Unknown pending action: ${state.pendingAction}")
                    conversationStateManager.clearState()
                    callback("कुछ गड़बड़ हो गई। फिर से कोशिश करें।", "none", "", false,"sad")
                }

            }
        }

        /**
         * Handle disambiguation when multiple contacts found
         */
        private fun handleDisambiguation(

            matches: List<ConversationStateManager.ContactOption>,
            originalIntent: String,
            callback: (String, String, String, Boolean,String) -> Unit
        ) {
            val isHindi = matches.firstOrNull()?.displayName?.matches(Regex(".*[\\u0900-\\u097F].*")) ?: false

            // Set state
            conversationStateManager.setState(
                action = "disambiguate_contact",
                options = matches,
                originalIntent = originalIntent
            )

            // Format response
            val reply = conversationStateManager.getFormattedOptions(isHindi)

            Log.d(TAG, "🔀 Disambiguation needed. Options: ${matches.size}")
            callback(reply, "none", "", true,"") // autoRestart = true for next input
        }
        /**
         * Handle SMS disambiguation when multiple contacts found
         */
        private fun handleSMSDisambiguation(
            matches: List<ConversationStateManager.ContactOption>,
            messageText: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            val isHindi = messageText.matches(Regex(".*[\\u0900-\\u097F].*"))

            // Set state with SMS message
            conversationStateManager.setState(
                action = "disambiguate_sms",
                options = matches,
                originalIntent = "sms"
            )
            conversationStateManager.getState().smsMessage = messageText

            // Format response
            val reply = conversationStateManager.getFormattedOptions(isHindi)

            Log.d(TAG, "📱 SMS disambiguation needed. Options: ${matches.size}")
            callback(reply, "none", "", true,"") // autoRestart = true
        }
        /**
         * Ask for SMS confirmation before sending
         */
        private fun handleSMSConfirmation(
            contactName: String,
            phoneNumber: String,
            messageText: String,
            callback: (String, String, String, Boolean,String) -> Unit
        ) {
            val isHindi = LanguageDetector.isHindiIntent(messageText)

            conversationStateManager.setState(
                action = "confirm_sms",
                selectedContact = contactName,
                selectedNumber = phoneNumber,
                originalIntent = "sms"
            )
            conversationStateManager.getState().smsMessage = messageText

            val confirmMsg = if (isHindi) {
                "$contactName को मैसेज भेज दूं कि - '$messageText'"
            } else {
                "Should I send '$messageText' to $contactName?"
            }

            Log.d(TAG, "📱 SMS confirmation requested")

            callback(confirmMsg, "none", "", true,"") // autoRestart = true
        }

        /**
         * Check memory for saved preferences and contact queries
         */
        private fun checkMemoryQuery(command: String): String? {
            val lowerCommand = command.lowercase()

            // Contact number queries - USE DISAMBIGUATION
            if (lowerCommand.contains("what is") ||
                lowerCommand.contains("what's") ||
                lowerCommand.contains("tell me")) {

                if (lowerCommand.contains("contact") ||
                    lowerCommand.contains("number") ||
                    lowerCommand.contains("phone")) {

                    val nameMatch = Regex("(?:of|for|का|के)\\s+(.+?)(?:\\s|$)", RegexOption.IGNORE_CASE).find(command)
                    if (nameMatch != null) {
                        val contactName = nameMatch.groupValues[1].trim()

                        // ✅ Use disambiguation-aware search
                        val searchResult = taskManager.searchContactWithDisambiguation(contactName.lowercase())

                        // Only return if SINGLE match, otherwise let OfflineIntelligence handle
                        if (searchResult.isSingleMatch && searchResult.singleContact != null) {
                            return "$contactName's number is ${searchResult.singleContact}"
                        }
                        // If multiple matches, return null to let OfflineIntelligence handle disambiguation
                        return null
                    }
                }

                // Check user preferences
                if (lowerCommand.contains("favorite") || lowerCommand.contains("my")) {
                    val memoryManager = llmManager.getMemoryManager()

                    if (lowerCommand.contains("color")) {
                        memoryManager.getUserPreference("favorite_color")?.let { color ->
                            return "Your favorite color is $color!"
                        }
                    }
                }
            }

            return null
        }

        /**
         * Save user preferences from conversation
         */
        private fun saveUserPreferences(command: String) {
            val lowerCommand = command.lowercase()
            val memoryManager = llmManager.getMemoryManager()

            if (lowerCommand.contains("my favorite") ||
                lowerCommand.contains("i like") ||
                lowerCommand.contains("i love")) {

                if (lowerCommand.contains("color")) {
                    val colorWords = listOf("black", "white", "red", "blue", "green", "yellow", "orange", "purple", "pink")
                    colorWords.forEach { color ->
                        if (lowerCommand.contains(color)) {
                            memoryManager.saveUserPreference("favorite_color", color)
                            Log.d(TAG, "💾 Saved: favorite_color = $color")
                        }
                    }
                }
            }
        }
        /**
         * Handle weather query
         */
        private suspend fun handleWeatherQuery(
            location: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage

                val weather = if (location == "current") {
                    weatherManager.getCurrentWeather()
                } else {
                    weatherManager.getCurrentWeather() // Fallback for now
                }

                if (weather != null) {
                    val weatherMsg = if (isHindi) {
                        weatherManager.formatWeatherMessageHindi(weather)
                    } else {
                        weatherManager.formatWeatherMessage(weather)
                    }

                    // Check for alerts
                    val alerts = weatherManager.analyzeWeatherForAlerts(weather)
                    val alertMsg = if (alerts.isNotEmpty()) {
                        "\n\n" + weatherManager.formatAlertMessage(alerts, isHindi)
                    } else ""

                    val suggestion = weatherManager.getWeatherSuggestion(weather, isHindi)
                    val fullMessage = "$weatherMsg. $suggestion$alertMsg"

                    val emotion = when {
                        weatherManager.willRain(weather) -> "calm"
                        weatherManager.isGoodWeather(weather) -> "happy"
                        else -> "neutral"
                    }

                    callback(fullMessage, "none", "", false, emotion)
                } else {
                    val errorMsg = if (isHindi) "मौसम की जानकारी नहीं मिल पाई sir।"
                    else "sir, Couldn't fetch weather information."
                    callback(errorMsg, "none", "", false, "sad")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weather query failed", e)
                callback("Had trouble getting weather.", "none", "", false, "sad")
            }
        }

        /**
         * Handle city-specific weather
         */
        private suspend fun handleCityWeather(
            cityName: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage

                val result = weatherManager.getWeatherForCity(cityName)

                if (result != null) {
                    val (weather, locationName) = result

                    if (weather != null) {
                        val weatherMsg = if (isHindi) {
                            weatherManager.formatWeatherMessageHindi(weather)
                        } else {
                            weatherManager.formatWeatherMessage(weather)
                        }

                        val fullMessage = if (isHindi) {
                            "$locationName का मौसम: $weatherMsg"
                        } else {
                            "Weather in $locationName: $weatherMsg"
                        }

                        callback(fullMessage, "none", "", false, "neutral")
                    } else {
                        callback("Couldn't get weather for $cityName", "none", "", false, "sad")
                    }
                } else {
                    val msg = if (isHindi) "$cityName नहीं मिला sir।"
                    else "sir, Couldn't find $cityName."
                    callback(msg, "none", "", false, "sad")
                }

            } catch (e: Exception) {
                Log.e(TAG, "City weather failed", e)
                callback("Error fetching city weather.", "none", "", false, "sad")
            }
        }

        /**
         * Handle weather forecast
         */
        private suspend fun handleWeatherForecast(
            location: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage

                val forecast = if (location == "current") {
                    weatherManager.getCurrentForecast()
                } else {
                    weatherManager.getForecastForCity(location)
                }

                if (forecast != null) {
                    val forecastMsg = if (isHindi) {
                        weatherManager.formatForecastMessageHindi(forecast)
                    } else {
                        weatherManager.formatForecastMessage(forecast)
                    }

                    callback(forecastMsg, "none", "", false, "neutral")
                } else {
                    val msg = if (isHindi) "पूर्वानुमान नहीं मिल पाया sir।"
                    else "sir, Couldn't get forecast."
                    callback(msg, "none", "", false, "sad")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Forecast failed", e)
                callback("Error fetching forecast.", "none", "", false, "sad")
            }
        }

        /**
         * Handle weather alerts
         */
        private suspend fun handleWeatherAlerts(
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage
                val weather = weatherManager.getCurrentWeather()

                if (weather != null) {
                    val alerts = weatherManager.analyzeWeatherForAlerts(weather)

                    if (alerts.isNotEmpty()) {
                        val alertMsg = weatherManager.formatAlertMessage(alerts, isHindi)
                        val emotion = if (alerts.any { it.severity == "severe" }) "urgent" else "calm"
                        callback(alertMsg, "none", "", false, emotion)
                    } else {
                        val msg = if (isHindi) "कोई मौसम चेतावनी नहीं। सब सुरक्षित है sir।"
                        else "No weather alerts sir. All clear."
                        callback(msg, "none", "", false, "happy")
                    }
                } else {
                    callback("Couldn't check weather alerts.", "none", "", false, "sad")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weather alerts failed", e)
                callback("Error checking alerts.", "none", "", false, "sad")
            }
        }

        /**
         * Check tomorrow's weather for specific condition
         */
        private suspend fun checkTomorrowWeather(
            condition: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage
                val (willHappen, message) = weatherManager.checkTomorrowWeather(condition)

                val reply = if (message != null) {
                    if (isHindi) {
                        when (condition) {
                            "rain" -> if (willHappen) "हां sir, कल बारिश हो सकती है। $message"
                            else "नहीं sir, कल बारिश नहीं होगी। $message"
                            "hot" -> if (willHappen) "हां sir, कल गर्मी होगी। $message"
                            else "नहीं sir, कल गर्मी नहीं होगी। $message"
                            else -> message
                        }
                    } else {
                        message
                    }
                } else {
                    if (isHindi) "कल के मौसम की जानकारी नहीं मिली sir।"
                    else "sir, Couldn't get tomorrow's weather."
                }

                callback(reply, "none", "", false, "neutral")

            } catch (e: Exception) {
                Log.e(TAG, "Tomorrow weather check failed", e)
                callback("Error checking tomorrow's weather.", "none", "", false, "sad")
            }
        }

        /**
         * Set umbrella reminder
         */
        private fun handleUmbrellaReminder(
            timeStr: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val hour = timeStr.toIntOrNull() ?: 7
                val success = weatherReminderManager.setUmbrellaReminder(hour)

                val isHindi = lastDetectedLanguage
                val reply = if (success) {
                    if (isHindi) "ठीक है sir, मैं रोज़ $hour बजे बारिश चेक करूंगा और याद दिलाऊंगा।"
                    else "Okay sir, I'll check for rain daily at $hour AM and remind you."
                } else {
                    if (isHindi) "अनुस्मारक सेट करने में समस्या हुई sir।"
                    else "sir, Had trouble setting reminder."
                }

                callback(reply, "none", "", false, if (success) "happy" else "sad")

            } catch (e: Exception) {
                Log.e(TAG, "Umbrella reminder failed", e)
                callback("Error setting reminder.", "none", "", false, "sad")
            }
        }

        /**
         * Show weather reminders
         */
        private fun handleShowWeatherReminders(
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage
                val info = weatherReminderManager.formatReminderInfo(isHindi)
                callback(info, "none", "", false, "neutral")

            } catch (e: Exception) {
                Log.e(TAG, "Show reminders failed", e)
                callback("Error showing reminders.", "none", "", false, "sad")
            }
        }

        /**
         * Cancel weather reminders
         */
        private fun handleCancelWeatherReminders(
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                weatherReminderManager.cancelAllReminders()

                val isHindi = lastDetectedLanguage
                val reply = if (isHindi) "सभी मौसम अनुस्मारक रद्द कर दिए sir।"
                else "sir, All weather reminders cancelled."

                callback(reply, "none", "", false, "neutral")

            } catch (e: Exception) {
                Log.e(TAG, "Cancel reminders failed", e)
                callback("Error cancelling reminders.", "none", "", false, "sad")
            }
        }

        /**
         * Handle weather-based suggestions
         */
        private suspend fun handleWeatherSuggestion(
            suggestionType: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage
                val weather = weatherManager.getCurrentWeather()

                if (weather != null) {
                    val reply = when (suggestionType) {
                        "umbrella" -> {
                            if (weatherManager.willRain(weather)) {
                                if (isHindi) "हां sir, छाता जरूर लेकर जाएं। बारिश हो सकती है।"
                                else "Yes sir, take an umbrella. It might rain."
                            } else {
                                if (isHindi) "नहीं sir, छाते की जरूरत नहीं। मौसम साफ है।"
                                else "No sir, you don't need an umbrella. Weather is clear."
                            }
                        }
                        "clothes" -> {
                            when {
                                weather.temperature < 10 -> {
                                    if (isHindi) "बहुत ठंड है sir। गर्म जैकेट, स्वेटर और मफलर पहनें। तापमान ${weather.temperature.toInt()}°C है।"
                                    else "It's very cold sir. Wear a warm jacket, sweater and muffler. Temperature is ${weather.temperature.toInt()}°C."
                                }
                                weather.temperature < 15 -> {
                                    if (isHindi) "ठंड है sir। गर्म कपड़े पहनें, स्वेटर या जैकेट लेकर जाएं। ${weather.temperature.toInt()}°C है।"
                                    else "It's cold sir. Wear warm clothes, take a sweater or jacket. It's ${weather.temperature.toInt()}°C."
                                }
                                weather.temperature < 20 -> {
                                    if (isHindi) "हल्की ठंड है sir। हल्का स्वेटर या शॉल काफी रहेगा।"
                                    else "It's mildly cold sir. A light sweater or shawl will be enough."
                                }
                                weather.temperature in 20.0..28.0 -> {
                                    if (isHindi) "मौसम अच्छा है sir। सामान्य कपड़े ठीक रहेंगे। ${weather.temperature.toInt()}°C है।"
                                    else "Weather is pleasant sir. Normal clothes are fine. It's ${weather.temperature.toInt()}°C."
                                }
                                weather.temperature in 28.0..35.0 -> {
                                    if (isHindi) "गर्मी है sir। हल्के और सूती कपड़े पहनें। तापमान ${weather.temperature.toInt()}°C है।"
                                    else "It's warm sir. Wear light and cotton clothes. Temperature is ${weather.temperature.toInt()}°C."
                                }
                                weather.temperature > 35 && weather.temperature <= 40 -> {
                                    if (isHindi) "बहुत गर्मी है sir। बहुत हल्के कपड़े पहनें, टोपी लगाएं और धूप से बचें। ${weather.temperature.toInt()}°C है।"
                                    else "It's very hot sir. Wear very light clothes, use a cap and avoid direct sunlight. It's ${weather.temperature.toInt()}°C."
                                }
                                weather.temperature > 40 -> {
                                    if (isHindi) "अत्यधिक गर्मी है sir! सफेद रंग के हल्के कपड़े पहनें, टोपी जरूर लगाएं और बाहर कम निकलें। ${weather.temperature.toInt()}°C है।"
                                    else "Extreme heat sir! Wear white light clothes, definitely use a cap and minimize going outside. It's ${weather.temperature.toInt()}°C."
                                }
                                else -> {
                                    if (isHindi) "सामान्य कपड़े ठीक रहेंगे sir।"
                                    else "Normal clothes are fine sir."
                                }
                            }
                        }
                        "outdoor" -> {
                            if (weatherManager.isGoodWeather(weather)) {
                                if (isHindi) "हां sir, बाहर जाने का अच्छा समय है। मौसम बहुत अच्छा है।"
                                else "Yes sir, it's a good time to go out. Weather is great."
                            } else if (weatherManager.willRain(weather)) {
                                if (isHindi) "नहीं sir, बारिश हो सकती है। घर पर रहना बेहतर है या छाता लेकर जाएं।"
                                else "No sir, it might rain. Better to stay home or take an umbrella."
                            } else if (weather.temperature > 38) {
                                if (isHindi) "नहीं sir, बहुत गर्मी है। दोपहर में बाहर न निकलें।"
                                else "No sir, it's too hot. Avoid going out in the afternoon."
                            } else if (weather.temperature < 10) {
                                if (isHindi) "बहुत ठंड है sir। जरूरी हो तो ही बाहर जाएं, गर्म कपड़े पहनकर।"
                                else "It's very cold sir. Go out only if necessary, with warm clothes."
                            } else {
                                if (isHindi) "बाहर जा सकते हैं sir, लेकिन मौसम बिल्कुल परफेक्ट नहीं है।"
                                else "You can go out sir, but weather isn't perfect."
                            }
                        }
                        "travel" -> {
                            val alerts = weatherManager.analyzeWeatherForAlerts(weather)
                            val severeAlerts = alerts.filter { it.severity == "severe" }

                            if (severeAlerts.isNotEmpty()) {
                                if (isHindi) "नहीं sir, यात्रा टालें। ${severeAlerts[0].messageHindi}"
                                else "No sir, avoid travel. ${severeAlerts[0].message}"
                            } else if (weatherManager.willRain(weather)) {
                                if (isHindi) "यात्रा कर सकते हैं sir, लेकिन बारिश हो सकती है। सावधान रहें।"
                                else "You can travel sir, but it might rain. Be careful."
                            } else if (weatherManager.isGoodWeather(weather)) {
                                if (isHindi) "हां sir, यात्रा के लिए अच्छा समय है। मौसम साफ है।"
                                else "Yes sir, it's a good time to travel. Weather is clear."
                            } else {
                                if (isHindi) "यात्रा कर सकते हैं sir, बस थोड़ा ध्यान रखें।"
                                else "You can travel sir, just be a bit careful."
                            }
                        }
                        "exercise" -> {
                            when {
                                weather.temperature < 5 -> {
                                    if (isHindi) "बहुत ठंड है sir। घर के अंदर exercise करें।"
                                    else "It's too cold sir. Exercise indoors."
                                }
                                weather.temperature in 5.0..15.0 -> {
                                    if (isHindi) "ठंड है sir। गर्म कपड़े पहनकर exercise कर सकते हैं, लेकिन warm-up जरूर करें।"
                                    else "It's cold sir. You can exercise with warm clothes, but warm up first."
                                }
                                weather.temperature in 15.0..28.0 -> {
                                    if (isHindi) "बहुत अच्छा समय है sir। बाहर exercise करने के लिए परफेक्ट मौसम है।"
                                    else "Perfect time sir. Great weather for outdoor exercise."
                                }
                                weather.temperature in 28.0..35.0 -> {
                                    if (isHindi) "गर्मी है sir। सुबह या शाम को exercise करें, पानी साथ रखें।"
                                    else "It's warm sir. Exercise in morning or evening, carry water."
                                }
                                weather.temperature > 35 -> {
                                    if (isHindi) "बहुत गर्मी है sir। घर के अंदर exercise करें या सुबह बहुत जल्दी जाएं।"
                                    else "It's very hot sir. Exercise indoors or go very early morning."
                                }
                                else -> {
                                    if (isHindi) "Exercise कर सकते हैं sir।"
                                    else "You can exercise sir."
                                }
                            }
                        }
                        else -> weatherManager.getWeatherSuggestion(weather, isHindi)
                    }

                    callback(reply, "none", "", false, "friendly")
                } else {
                    val errorMsg = if (isHindi) "मौसम की जानकारी नहीं मिल पाई sir।"
                    else "sir, Couldn't check weather."
                    callback(errorMsg, "none", "", false, "sad")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weather suggestion failed", e)
                val errorMsg = if (lastDetectedLanguage) "सुझाव देने में समस्या हुई।"
                else "Had trouble giving suggestion."
                callback(errorMsg, "none", "", false, "sad")
            }
        }
        /**
         * Handle set reminder command
         */
        private fun handleSetReminder(
            command: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            Log.d(TAG,"inside handleSetRemainder")
            try {
                val (success, message) = reminderManager.setReminderFromCommand(command)
                Log.d(TAG,"inside handleSetRemainder succes $success")
                Log.d(TAG,"inside handleSetRemainder message $message")
                val emotion = if (success) "happy" else "sad"
                callback(message, "none", "", false, emotion)

            } catch (e: Exception) {
                Log.e(TAG, "Set reminder failed", e)
                callback("Error setting reminder", "none", "", false, "sad")
            }
        }

        /**
         * Show all active reminders
         */
        private fun handleShowReminders(
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage
                val reminders = reminderManager.getAllReminders()

                if (reminders.isEmpty()) {
                    val msg = if (isHindi) "कोई reminder सेट नहीं है sir।"
                    else "No reminders set sir."
                    callback(msg, "none", "", false, "neutral")
                } else {
                    val sb = StringBuilder()
                    if (isHindi) {
                        sb.append("आपके ${reminders.size} reminders:\n\n")
                    } else {
                        sb.append("Your ${reminders.size} reminders:\n\n")
                    }

                    reminders.forEachIndexed { index, reminder ->
                        val timeLeft = (reminder.triggerTime - System.currentTimeMillis()) / 1000 / 60
                        val actionText = when (reminder.action) {
                            "call" -> if (isHindi) "${reminder.target} को कॉल करना"
                            else "Call ${reminder.target}"
                            "message" -> if (isHindi) "${reminder.target} को मैसेज करना"
                            else "Message ${reminder.target}"
                            "meet" -> if (isHindi) "${reminder.target} से मिलना"
                            else "Meet ${reminder.target}"
                            else -> reminder.target
                        }

                        val timeText = if (timeLeft > 0) {
                            if (isHindi) "$timeLeft मिनट में" else "in $timeLeft minutes"
                        } else {
                            if (isHindi) "अब" else "now"
                        }

                        sb.append("${index + 1}. $actionText - $timeText\n")
                    }

                    callback(sb.toString().trim(), "none", "", false, "neutral")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Show reminders failed", e)
                callback("Error showing reminders", "none", "", false, "sad")
            }
        }

        /**
         * Cancel all reminders
         */
        private fun handleCancelReminders(
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val reminders = reminderManager.getAllReminders()
                val count = reminders.size

                if (count == 0) {
                    val isHindi = lastDetectedLanguage
                    val msg = if (isHindi) "कोई reminder cancel करने के लिए नहीं है sir।"
                    else "No reminders to cancel sir."
                    callback(msg, "none", "", false, "neutral")
                } else {
                    reminders.forEach { reminderManager.cancelReminder(it.id) }

                    val isHindi = lastDetectedLanguage
                    val msg = if (isHindi) "$count reminders cancel कर दिए sir।"
                    else "$count reminders cancelled sir."
                    callback(msg, "none", "", false, "neutral")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Cancel reminders failed", e)
                callback("Error cancelling reminders", "none", "", false, "sad")
            }
        }

        /**
         * Get task manager instance (useful for MainActivity)
         */
        fun getTaskManager(): TaskAutomationManager = taskManager
        fun getWeatherManager(): WeatherManager = weatherManager
        suspend fun isServerConnected(): Boolean {
            return try {
                llmManager.testConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Server connection check failed", e)
                false
            }
        }
        /**
         * Handle email command from voice
         */
        private fun handleEmailCommand(
            command: String,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                val isHindi = lastDetectedLanguage

                Log.d(TAG, "📧 Processing email command: $command")

                // Parse email command
                val parsed = emailManager.parseEmailCommand(command, isHindi)

                if (parsed == null) {
                    val errorMsg = if (isHindi)
                        "Email समझने में समस्या हुई sir।"
                    else
                        "sir, Couldn't understand the email command."
                    callback(errorMsg, "none", "", false, "sad")
                    return
                }

                // Check if content is missing
                if (parsed.body.isEmpty() || parsed.subject.isNullOrEmpty()) {
                    Log.d(TAG, "📧 Email content missing, starting collection")

                    // Start email content collection
                    startEmailContentCollection(
                        recipientName = parsed.recipientName,
                        recipientEmail = parsed.recipientEmail,
                        scheduleTime = parsed.scheduleTime,
                        isHindi = isHindi,
                        callback = callback
                    )
                } else {
                    // Content exists, send directly
                    Log.d(TAG, "📧 Email content complete, sending")

                    sendEmailWithConfirmation(
                        parsed = parsed,
                        isHindi = isHindi,
                        callback = callback
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Email command failed", e)
                val errorMsg = if (lastDetectedLanguage)
                    "Email भेजने में समस्या हुई।"
                else
                    "Had trouble sending email."
                callback(errorMsg, "none", "", false, "sad")
            }
        }

        /**
         * Start collecting email content via voice
         */
        private fun startEmailContentCollection(
            recipientName: String,
            recipientEmail: String,
            scheduleTime: Long?,
            isHindi: Boolean,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                // Set conversation state to collect email content
                conversationStateManager.setState(
                    action = "collect_email_content",
                    originalIntent = "send_email"
                )

                // Store email data in state
                conversationStateManager.getState().apply {
                    this.emailRecipient = recipientName
                    this.emailRecipientEmail = recipientEmail
                    this.emailScheduleTime = scheduleTime
                }

                // Ask user for email content
                val promptMsg = if (isHindi) {
                    "$recipientName को क्या mail भेजना है sir?"
                } else {
                    "sir, What should I send to $recipientName?"
                }

                Log.d(TAG, "📧 Asking for email content")

                // Return with autoRestart = true (mic will stay on)
                callback(promptMsg, "none", "", true, "friendly")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Email content collection failed", e)
                callback("Error starting email collection", "none", "", false, "sad")
            }
        }

        /**
         * Show email confirmation via voice
         */
        private fun showEmailConfirmationFromService(
            recipientName: String,
            recipientEmail: String,
            subject: String,
            body: String,
            scheduleTime: Long?,
            isHindi: Boolean,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                // Set confirmation state
                conversationStateManager.setState(
                    action = "confirm_email_send",
                    originalIntent = "send_email"
                )

                conversationStateManager.getState().apply {
                    this.emailRecipient = recipientName
                    this.emailRecipientEmail = recipientEmail
                    this.emailSubject = subject
                    this.emailBody = body
                    this.emailScheduleTime = scheduleTime
                }

                // Format confirmation message (short version for voice)
                val confirmMsg = if (isHindi) {
                    "$recipientName को email भेज दूं? विषय: $subject. संदेश: ${body.take(50)}... भेजूं?"
                } else {
                    "Should I send email to $recipientName? Subject: $subject. Message: ${body.take(50)}... Send it?"
                }

                Log.d(TAG, "📧 Email confirmation shown")

                // Ask for confirmation (autoRestart = true)
                callback(confirmMsg, "none", "", true, "friendly")

            } catch (e: Exception) {
                Log.e(TAG, "Email confirmation failed", e)
                conversationStateManager.clearState()
                callback("Error showing confirmation", "none", "", false, "sad")
            }
        }

        /**
         * Send email with confirmation (when content already provided)
         */
        private fun sendEmailWithConfirmation(
            parsed: EmailManager.ParsedEmailCommand,
            isHindi: Boolean,
            callback: (String, String, String, Boolean, String) -> Unit
        ) {
            try {
                showEmailConfirmationFromService(
                    recipientName = parsed.recipientName,
                    recipientEmail = parsed.recipientEmail,
                    subject = parsed.subject ?: "",
                    body = parsed.body,
                    scheduleTime = parsed.scheduleTime,
                    isHindi = isHindi,
                    callback = callback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Send email confirmation failed", e)
                callback("Error with email", "none", "", false, "sad")
            }
        }

    /**
     * Calculate delay based on text length
     */
    private fun calculateSpeechDelay(text: String): Long {
        // Rough estimate: 150 words per minute = 2.5 words per second
        val wordCount = text.split(" ").size
        val estimatedSeconds = (wordCount / 2.5).toLong()

        // Minimum 2 seconds, maximum 5 seconds
        return (estimatedSeconds * 1000).coerceIn(2000, 5000)
    }
    // 5️⃣ ADD BIRTHDAY COMMAND HANDLER FUNCTION
    private suspend fun handleBirthdayCommand(
        command: String,
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = LanguageDetector.isHindiIntent(command)
        val lowerCmd = command.lowercase()

        try {
            when {
                lowerCmd.contains("set") && (lowerCmd.contains("birthday") || lowerCmd.contains("जन्मदिन")) -> {
                    handleSetBirthday(command, callback)
                }

                lowerCmd.contains("when") || lowerCmd.contains("कब") -> {
                    handleWhenBirthday(command, callback)
                }

                lowerCmd.contains("show") || lowerCmd.contains("list") || lowerCmd.contains("दिखाओ") -> {
                    handleShowBirthdays(callback)
                }

                lowerCmd.contains("upcoming") || lowerCmd.contains("next") || lowerCmd.contains("आने वाले") -> {
                    handleUpcomingBirthdays(callback)
                }

                lowerCmd.contains("scan") || lowerCmd.contains("refresh") || lowerCmd.contains("स्कैन") -> {
                    handleScanContacts(callback)
                }

                else -> {
                    val msg = if (isHindi) {
                        "मुझे समझ नहीं आया sir। आप कह सकते हैं:\n" +
                                "- 'Rahul की birthday 15 March को set करो'\n" +
                                "- 'Rahul की birthday कब है?'\n" +
                                "- 'सभी birthdays दिखाओ'"
                    } else {
                        "I didn't understand sir. You can say:\n" +
                                "- 'Set Rahul's birthday on 15th March'\n" +
                                "- 'When is Rahul's birthday?'\n" +
                                "- 'Show all birthdays'"
                    }
                    callback(msg, "none", "", false, "neutral")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling birthday command", e)
            val msg = if (isHindi) "Birthday command में समस्या हुई sir।"
            else "Had trouble with birthday command sir."
            callback(msg, "none", "", false, "sad")
        }
    }

    private suspend fun handleSetBirthday(
        command: String,
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = LanguageDetector.isHindiIntent(command)

        try {
            val namePattern = Regex("set\\s+(.+?)'s?\\s+birthday", RegexOption.IGNORE_CASE)
            val datePattern = Regex("on\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+(\\w+)(?:\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)

            val nameMatch = namePattern.find(command)
            val dateMatch = datePattern.find(command)

            if (nameMatch != null && dateMatch != null) {
                val name = nameMatch.groupValues[1].trim()
                val day = dateMatch.groupValues[1].toInt()
                val monthName = dateMatch.groupValues[2]
                val year = dateMatch.groupValues[3].toIntOrNull() ?: 1900

                val months = mapOf(
                    "january" to 1, "jan" to 1,
                    "february" to 2, "feb" to 2,
                    "march" to 3, "mar" to 3,
                    "april" to 4, "apr" to 4,
                    "may" to 5,
                    "june" to 6, "jun" to 6,
                    "july" to 7, "jul" to 7,
                    "august" to 8, "aug" to 8,
                    "september" to 9, "sep" to 9,
                    "october" to 10, "oct" to 10,
                    "november" to 11, "nov" to 11,
                    "december" to 12, "dec" to 12
                )

                val month = months[monthName.lowercase()] ?: 1
                val dateOfBirth = String.format("%02d/%02d/%04d", day, month, year)

                val success = birthdayManager.setContactBirthday(name, dateOfBirth)

                val reply = if (success) {
                    if (isHindi) {
                        "ठीक है sir, मैंने $name की birthday $day $monthName को save कर दी।"
                    } else {
                        "Okay sir, I've saved ${name}'s birthday as $day $monthName."
                    }
                } else {
                    if (isHindi) {
                        "$name का contact नहीं मिला sir। पहले contact add करें।"
                    } else {
                        "sir, Couldn't find $name in contacts. Please add them first."
                    }
                }

                callback(reply, "none", "", false, if (success) "happy" else "sad")

            } else {
                val msg = if (isHindi) {
                    "कृपया ऐसे बोलें: 'Rahul की birthday 15 March को set करो'"
                } else {
                    "Please say like: 'Set Rahul's birthday on 15th March'"
                }
                callback(msg, "none", "", true, "neutral")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting birthday", e)
            callback("Error setting birthday", "none", "", false, "sad")
        }
    }

    private suspend fun handleWhenBirthday(
        command: String,
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = LanguageDetector.isHindiIntent(command)

        try {
            val pattern = Regex("(?:when is|कब है)\\s+(.+?)(?:'s)?\\s+(?:birthday|जन्मदिन)", RegexOption.IGNORE_CASE)
            val match = pattern.find(command)

            if (match != null) {
                val name = match.groupValues[1].trim()
                val contact = birthdayManager.getContactByName(name)

                val reply = if (contact != null && contact.dateOfBirth != null) {
                    val formatted = contact.getFormattedBirthday()
                    val age = contact.getAge()

                    if (isHindi) {
                        if (age != null && age > 0 && age < 120) {
                            "${contact.name} की birthday $formatted है। वे ${age} साल के हैं।"
                        } else {
                            "${contact.name} की birthday $formatted है।"
                        }
                    } else {
                        if (age != null && age > 0 && age < 120) {
                            "${contact.name}'s birthday is on $formatted. They are $age years old."
                        } else {
                            "${contact.name}'s birthday is on $formatted."
                        }
                    }
                } else {
                    if (isHindi) {
                        "$name की birthday save नहीं है sir। पहले set करें।"
                    } else {
                        "sir, I don't have ${name}'s birthday saved. Please set it first."
                    }
                }

                callback(reply, "none", "", false, "neutral")

            } else {
                val msg = if (isHindi) "किसकी birthday जानना चाहते हैं sir?"
                else "Whose birthday do you want to know sir?"
                callback(msg, "none", "", true, "neutral")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking birthday", e)
            callback("Error checking birthday", "none", "", false, "sad")
        }
    }

    private suspend fun handleShowBirthdays(
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = lastDetectedLanguage

        try {
            val birthdays = birthdayManager.getAllBirthdays()

            if (birthdays.isEmpty()) {
                val msg = if (isHindi) "कोई birthday save नहीं है sir।"
                else "No birthdays saved sir."
                callback(msg, "none", "", false, "neutral")
            } else {
                val sb = StringBuilder()
                if (isHindi) {
                    sb.append("${birthdays.size} birthdays save हैं:\n\n")
                } else {
                    sb.append("${birthdays.size} birthdays saved:\n\n")
                }

                birthdays.forEachIndexed { index, contact ->
                    val formatted = contact.getFormattedBirthday()
                    sb.append("${index + 1}. ${contact.name} - $formatted\n")
                }

                callback(sb.toString().trim(), "none", "", false, "neutral")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing birthdays", e)
            callback("Error showing birthdays", "none", "", false, "sad")
        }
    }

    private suspend fun handleUpcomingBirthdays(
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = lastDetectedLanguage

        try {
            val upcoming = birthdayManager.getUpcomingBirthdays()

            if (upcoming.isEmpty()) {
                val msg = if (isHindi) "अगले 30 दिन में कोई birthday नहीं है sir।"
                else "No birthdays in the next 30 days sir."
                callback(msg, "none", "", false, "neutral")
            } else {
                val sb = StringBuilder()
                if (isHindi) {
                    sb.append("अगले 30 दिन में ${upcoming.size} birthdays:\n\n")
                } else {
                    sb.append("${upcoming.size} upcoming birthdays:\n\n")
                }

                upcoming.forEachIndexed { index, contact ->
                    val formatted = contact.getFormattedBirthday()
                    sb.append("${index + 1}. ${contact.name} - $formatted\n")
                }

                callback(sb.toString().trim(), "none", "", false, "happy")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing upcoming birthdays", e)
            callback("Error showing upcoming birthdays", "none", "", false, "sad")
        }
    }

    private suspend fun handleScanContacts(
        callback: (String, String, String, Boolean, String) -> Unit
    ) {
        val isHindi = lastDetectedLanguage

        try {
            callback(
                if (isHindi) "Contacts scan कर रहा हूं sir..."
                else "Scanning contacts sir...",
                "none", "", false, "neutral"
            )

            val count = birthdayManager.scanAndSaveContacts()
            val stats = birthdayManager.getStats()

            val reply = if (isHindi) {
                "$count contacts scan किए। ${stats.contactsWithBirthdays} में birthday save है।"
            } else {
                "Scanned $count contacts. ${stats.contactsWithBirthdays} have birthdays saved."
            }

            callback(reply, "none", "", false, "happy")

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning contacts", e)
            callback("Error scanning contacts", "none", "", false, "sad")
        }
    }
    /**
     * Check if birthday alarm is scheduled
     */
    private fun checkAlarmStatus() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(this, com.example.groot.birthday.BirthdayReceiver::class.java).apply {
                action = com.example.groot.birthday.BirthdayManager.ACTION_BIRTHDAY_CHECK
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                9999,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                Log.d(TAG, "✅ Midnight birthday alarm IS scheduled")
            } else {
                Log.w(TAG, "⚠️ Midnight birthday alarm NOT scheduled!")
            }

            // Check exact alarm permission (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    Log.d(TAG, "✅ App CAN schedule exact alarms")
                } else {
                    Log.e(TAG, "❌ App CANNOT schedule exact alarms! Permission needed.")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Alarm check failed", e)
        }
    }
}