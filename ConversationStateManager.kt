package com.example.groot

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages multi-turn conversation state for disambiguation and confirmations
 */
class ConversationStateManager {

    companion object {
        private const val TAG = "ConversationStateManager"
        private const val STATE_TIMEOUT_MS = 40000L // 30 seconds
    }

    data class ConversationState(
        var pendingAction: String = "none", // "disambiguate_contact", "confirm_call", "confirm_sms", "none"
        var disambiguationOptions: List<ContactOption> = emptyList(),
        var selectedContact: String? = null,
        var selectedNumber: String? = null,
        var originalIntent: String = "", // "call", "get_number", "sms"
        var failureCount: Int = 0,
        var timestamp: Long = System.currentTimeMillis(),
        var currentPage: Int = 0,  // NEW
        var pageSize: Int = 5,    // NEW
        var smsMessage: String? = null,  // NEW - for SMS text
        var smsRecipient: String? = null,  // NEW - for multi-turn SMS
        var lastAction: String = "",
        var lastContact: String = "",
        var lastSuccessfulAction: Long = 0L
    )

    data class ContactOption(
        val displayName: String,
        val number: String
    )

    private var currentState = ConversationState()
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    var onTimeout: ((String) -> Unit)? = null

    /**
     * Set new conversation state
     */
    fun setState(
        action: String,
        options: List<ContactOption> = emptyList(),
        selectedContact: String? = null,
        selectedNumber: String? = null,
        originalIntent: String = ""
    ) {
        clearTimeout()

        currentState = ConversationState(
            pendingAction = action,
            disambiguationOptions = options,
            selectedContact = selectedContact,
            selectedNumber = selectedNumber,
            originalIntent = originalIntent,
            failureCount = 0,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "State set: action=$action, options=${options.size}, intent=$originalIntent")

        if (action != "none") {
            startTimeout()
        }
    }

    /**
     * Get current state
     */
    fun getState(): ConversationState {
        return currentState
    }

    /**
     * Check if there's a pending action
     */
    fun hasPendingAction(): Boolean {
        return currentState.pendingAction != "none"
    }

    /**
     * Clear current state
     */
    fun clearState() {
        clearTimeout()
        currentState = ConversationState()
        Log.d(TAG, "State cleared")
    }

    /**
     * Increment failure count
     */
    fun incrementFailure() {
        currentState.failureCount++
        Log.w(TAG, "Failure count: ${currentState.failureCount}")
    }

    /**
     * Match user input against disambiguation options
     */
    fun matchDisambiguation(userInput: String): ContactOption? {
        val cleanInput = userInput.trim().lowercase()
        val options = currentState.disambiguationOptions

        if (options.isEmpty()) {
            Log.w(TAG, "No disambiguation options available")
            return null
        }

        // Try exact match
        options.forEach { option ->
            if (option.displayName.lowercase() == cleanInput) {
                Log.d(TAG, "Exact match found: ${option.displayName}")
                return option
            }
        }

        // Try number-based selection ("1", "number 2", "second one")
        val numberMatch = extractNumber(cleanInput)
        if (numberMatch != null && numberMatch > 0 && numberMatch <= options.size) {
            val selected = options[numberMatch - 1]
            Log.d(TAG, "Number-based match: $numberMatch -> ${selected.displayName}")
            return selected
        }

        // Try partial match (must contain significant part)
        options.forEach { option ->
            val optionLower = option.displayName.lowercase()

            // Check if input contains option or vice versa
            if (cleanInput.contains(optionLower) || optionLower.contains(cleanInput)) {
                // But avoid too short matches (like "ra" matching "Rahul")
                if (cleanInput.length >= 3 || optionLower.startsWith(cleanInput)) {
                    Log.d(TAG, "Partial match found: ${option.displayName}")
                    return option
                }
            }
        }

        // Try fuzzy match (Levenshtein distance)
        val fuzzyMatches = options.map { option ->
            Pair(option, levenshteinDistance(cleanInput, option.displayName.lowercase()))
        }.sortedBy { it.second }

        val bestMatch = fuzzyMatches.firstOrNull()
        if (bestMatch != null && bestMatch.second <= 3) { // Allow up to 3 character differences
            Log.d(TAG, "Fuzzy match found: ${bestMatch.first.displayName} (distance: ${bestMatch.second})")
            return bestMatch.first
        }

        Log.w(TAG, "No match found for input: $cleanInput")
        return null
    }

    /**
     * Check if user response is positive (yes/haan/karo)
     */
    fun isPositiveResponse(input: String): Boolean {
        val cleanInput = input.trim().lowercase()

        val positiveWords = listOf(
            "yes", "yeah", "yup", "ok", "okay", "sure", "karo", "करो",
            "haan","han", "हां", "ha", "yes please", "call karo", "कॉल करो"
        )

        return positiveWords.any { cleanInput.contains(it) }
    }

    /**
     * Check if user response is negative (no/nahi/mat karo)
     */
    fun isNegativeResponse(input: String): Boolean {
        val cleanInput = input.trim().lowercase()

        val negativeWords = listOf(
            "no", "nope", "nahi", "नहीं", "mat karo", "मत करो",
            "cancel", "stop", "रोको", "छोड़ो"
        )

        return negativeWords.any { cleanInput.contains(it) }
    }

    /**
     * Extract number from text (e.g., "number 2" -> 2, "second" -> 2)
     */
    private fun extractNumber(input: String): Int? {
        // Direct digit
        val digitMatch = Regex("\\d+").find(input)
        if (digitMatch != null) {
            return digitMatch.value.toIntOrNull()
        }

        // Word-based numbers
        val numberWords = mapOf(
            "first" to 1, "पहला" to 1, "one" to 1,
            "second" to 2, "दूसरा" to 2, "two" to 2,
            "third" to 3, "तीसरा" to 3, "three" to 3,
            "fourth" to 4, "चौथा" to 4, "four" to 4,
            "fifth" to 5, "पांचवा" to 5, "five" to 5
        )

        numberWords.forEach { (word, num) ->
            if (input.contains(word)) {
                return num
            }
        }

        return null
    }

    /**
     * Calculate Levenshtein distance (edit distance) between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Start timeout timer
     */
    private fun startTimeout() {
        timeoutRunnable = Runnable {
            Log.w(TAG, "State timeout - clearing state")
            val action = currentState.pendingAction
            clearState()
            onTimeout?.invoke("Timeout after 30 seconds for action: $action")
        }
        handler.postDelayed(timeoutRunnable!!, STATE_TIMEOUT_MS)
        Log.d(TAG, "Timeout started (30s)")
    }

    /**
     * Clear timeout timer
     */
    private fun clearTimeout() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    /**
     * Get formatted list of options for user
     */
    /**
     * Get formatted list of options for user (with pagination)
     */
    fun getFormattedOptions(isHindi: Boolean, showPage: Int = 0): String {
        val options = currentState.disambiguationOptions
        if (options.isEmpty()) return ""

        val pageSize = currentState.pageSize
        val totalPages = (options.size + pageSize - 1) / pageSize
        val currentPage = showPage.coerceIn(0, totalPages - 1)

        currentState.currentPage = currentPage

        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, options.size)
        val pageOptions = options.subList(startIndex, endIndex)

        val prefix = if (isHindi) {
            if (options.size <= pageSize) {
                "आपके फोन में ${options.size} contacts हैं:\n"
            } else {
                "आपके फोन में ${options.size} contacts हैं। पहले $pageSize दिखा रहा हूं:\n"
            }
        } else {
            if (options.size <= pageSize) {
                "You have ${options.size} contacts:\n"
            } else {
                "You have ${options.size} contacts. Showing first $pageSize:\n"
            }
        }

        val list = pageOptions.mapIndexed { index, option ->
            val globalIndex = startIndex + index + 1
            "$globalIndex. ${option.displayName}"
        }.joinToString("\n")

        val suffix = if (options.size > endIndex) {
            if (isHindi) "\n\nअगले ${minOf(pageSize, options.size - endIndex)} के लिए 'next' बोलें, या number select करें।"
            else "\n\nSay 'next' for next ${minOf(pageSize, options.size - endIndex)}, or select a number."
        } else {
            if (isHindi) "\n\nकिसे select करना है?"
            else "\n\nWhich one do you want?"
        }

        return prefix + list + suffix
    }

    /**
     * Check if user said "next" for pagination
     */
    fun isNextPageRequest(input: String): Boolean {
        val cleanInput = input.trim().lowercase()
        return cleanInput.contains("next") ||
                cleanInput.contains("aage") ||
                cleanInput.contains("आगे") ||
                cleanInput.contains("और") ||
                cleanInput.contains("more")
    }

    /**
     * Get next page of options
     */
    fun getNextPage(isHindi: Boolean): String? {
        val totalPages = (currentState.disambiguationOptions.size + currentState.pageSize - 1) / currentState.pageSize
        val nextPage = currentState.currentPage + 1

        return if (nextPage < totalPages) {
            getFormattedOptions(isHindi, nextPage)
        } else {
            if (isHindi) "यह आखिरी page था। Number select करें।"
            else "That was the last page. Please select a number."
        }
    }
    fun recordAction(action: String, contact: String) {
        currentState.lastAction = action
        currentState.lastContact = contact
        currentState.lastSuccessfulAction = System.currentTimeMillis()
    }

    fun getRecentContact(): String? {
        val timeSinceAction = System.currentTimeMillis() - currentState.lastSuccessfulAction
        return if (timeSinceAction < 120000 && currentState.lastContact.isNotEmpty()) {
            currentState.lastContact
        } else null
    }
}