package com.example.groot

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.delay

class GrootService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var llmManager: LLMManager
    private lateinit var taskManager: TaskAutomationManager
    private val offlineIntelligence = OfflineIntelligence()

    private val conversationStateManager = ConversationStateManager()
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
        // Setup timeout callback
        conversationStateManager.onTimeout = { message ->
            Log.w(TAG, "⏰ State timeout: $message")
            // Optionally notify user via some mechanism
        }
        Log.d(TAG, "GrootService created")
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
     * Main command processing with TaskAutomationManager integration
     */
    fun processCommand(command: String, callback: (String, String, String, Boolean) -> Unit) {

        Log.d(TAG, "🎤 Received command: $command")
        serviceScope.launch {

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
                    callback(memoryResponse, "none", "", false)
                    return@launch
                }

                // STEP 2: Try offline intelligence
                Log.d(TAG, "🔍 Trying offline intelligence...$command")
                val offlineResponse = offlineIntelligence.handleOffline(command, taskManager)

                if (offlineResponse.handled) {
                    Log.i(TAG, "✅ Handled offline: ${offlineResponse.reply}")

                    // Handle prompt_continue action
                    if (offlineResponse.action == "prompt_continue") {
                        callback(offlineResponse.reply, "none", "", true)
                        return@launch
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
                                "किसके बारे में बता रहे हैं आप?"
                            } else {
                                "Who are you referring to?"
                            }
                            callback(reply, "none", "", true)
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
                        callback(offlineResponse.reply, "none", "", true)
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

                    callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target, false)
                    return@launch
                }

                // STEP 3: Save preferences
                saveUserPreferences(command)

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

                callback(reply, action, target, false)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)
                val offlineResponse = offlineIntelligence.handleOffline(command, taskManager)
                if (offlineResponse.handled) {
                    callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target, false)
                } else {
                    callback("I'm sorry, I couldn't process that.", "none", "", false)
                }
            }
        }
    }

    /**
     * Ask user if they need more help and restart mic
     */

    private fun promptContinue(callback: (String, String, String, Boolean) -> Unit) {
        val message = ResponseVariations.getRandomPrompt(lastDetectedLanguage)
        callback(message, "none", "", true)
    }


    /**
     * Handle commands when there's pending state (disambiguation/confirmation)
     */
    private fun handleStatefulCommand(command: String, callback: (String, String, String, Boolean) -> Unit) {
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
                        callback(nextPageText, "none", "", true) // autoRestart = true
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

                            callback(reply + confirmPrompt, "none", "", true) // autoRestart = true
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
                            callback(confirmMsg, "call", matched.number, false)

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
                        callback(reply, "none", "", false)
                    } else {
                        val reply = if (isHindi)
                            "मैंने सही से नहीं सुना। कृपया फिर से बोलें किसे select करना है?"
                        else
                            "Sorry, I didn't catch that. Please say again which one to select?"

                        callback(reply, "none", "", true) // autoRestart = true
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

                    conversationStateManager.recordAction("call", state.selectedContact ?: "")
                    conversationStateManager.clearState()

                    callback(reply, "call", state.selectedNumber ?: "", false)

                    // Register callback for when call ends
                    taskManager.setCallEndCallback {
                        serviceScope.launch {
                            delay(10000)
                            val completionMsg = if (isHindi) "कॉल समाप्त हो गई।"
                            else "Call ended."
                            callback(completionMsg, "none", "", false)

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
                    val reply = if (isHindi) "ठीक है, कॉल नहीं कर रहा।"
                    else "Okay, not calling."
                    conversationStateManager.clearState()
                    callback(reply, "none", "", false)
                } else {
                    val reply = ResponseVariations.getConfusionResponse(isHindi)
                    callback(reply, "none", "", true)
                }
            }
            "disambiguate_sms" -> {
                val isHindi = LanguageDetector.isHindiIntent(command)
                // Check for next page
                if (conversationStateManager.isNextPageRequest(command)) {
                    val nextPageText = conversationStateManager.getNextPage(isHindi)
                    if (nextPageText != null) {
                        callback(nextPageText, "none", "", true)
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
                        callback(reply, "none", "", false)
                    } else {
                        val reply = ResponseVariations.getConfusionResponse(isHindi)
                        callback(reply, "none", "", true)
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

                    conversationStateManager.recordAction("sms", state.selectedContact ?: "")

                    val target = "${state.selectedContact}:${state.smsMessage}"
                    conversationStateManager.clearState()
                    callback(reply, "sms", target, false)

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

                            callback(statusReply, "none", "", false)
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
                                callback(
                                    if (isHindi) "संपर्क नहीं मिला" else "Contact not found",
                                    "none",
                                    "",
                                    false
                                )
                            }
                        }
                    }
                } else if (conversationStateManager.isNegativeResponse(command)) {
                    val reply = if (isHindi) "ठीक है, मैसेज नहीं भेज रहा।"
                    else "Okay, not sending the message."

                    conversationStateManager.clearState()
                    callback(reply, "none", "", false)
                }else {
                    val reply = ResponseVariations.getConfusionResponse(isHindi)
                    callback(reply, "none", "", true)
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
                        callback(reply, "none", "", false)
                    }
                } else {
                    // Invalid message
                    val reply = if (isHindi) "मैंने कुछ नहीं सुना। क्या मैसेज भेजना है?"
                    else "I didn't hear anything. What message do you want to send?"
                    callback(reply, "none", "", true)
                }
            }

            else -> {
                Log.w(TAG, "Unknown pending action: ${state.pendingAction}")
                conversationStateManager.clearState()
                callback("कुछ गड़बड़ हो गई। फिर से कोशिश करें।", "none", "", false)
            }

        }
    }

    /**
     * Handle disambiguation when multiple contacts found
     */
    private fun handleDisambiguation(

        matches: List<ConversationStateManager.ContactOption>,
        originalIntent: String,
        callback: (String, String, String, Boolean) -> Unit
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
        callback(reply, "none", "", true) // autoRestart = true for next input
    }
    /**
     * Handle SMS disambiguation when multiple contacts found
     */
    private fun handleSMSDisambiguation(
        matches: List<ConversationStateManager.ContactOption>,
        messageText: String,
        callback: (String, String, String, Boolean) -> Unit
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
        callback(reply, "none", "", true) // autoRestart = true
    }
    /**
     * Ask for SMS confirmation before sending
     */
    private fun handleSMSConfirmation(
        contactName: String,
        phoneNumber: String,
        messageText: String,
        callback: (String, String, String, Boolean) -> Unit
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
        callback(confirmMsg, "none", "", true) // autoRestart = true
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
     * Get task manager instance (useful for MainActivity)
     */
    fun getTaskManager(): TaskAutomationManager = taskManager
    suspend fun isServerConnected(): Boolean {
        return try {
            llmManager.testConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Server connection check failed", e)
            false
        }
    }
}