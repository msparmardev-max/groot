package com.example.groot

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_CONVERSATIONS = 500 // Increased for long-term memory
        private const val PREFS_NAME = "groot_memory"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_USER_PREFERENCES = "user_preferences"
    }

    private val conversations = mutableListOf<ConversationEntry>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Load all past conversations when app starts
        loadConversationsFromStorage()
    }

    data class ConversationEntry(
        val message: String,
        val timestamp: String,
        val type: ConversationType
    )

    enum class ConversationType {
        USER_INPUT,
        ASSISTANT_RESPONSE,
        SYSTEM_MESSAGE
    }

    /**
     * Add a conversation message to memory
     * Automatically saves to permanent storage
     */
    fun addConversation(message: String) {
        val timestamp = dateFormatter.format(Date())
        val type = when {
            message.startsWith("User:") -> ConversationType.USER_INPUT
            message.startsWith("Assistant:") -> ConversationType.ASSISTANT_RESPONSE
            message.startsWith("System:") -> ConversationType.SYSTEM_MESSAGE
            else -> ConversationType.SYSTEM_MESSAGE
        }

        val entry = ConversationEntry(
            message = message,
            timestamp = timestamp,
            type = type
        )

        conversations.add(entry)

        // Keep only the most recent conversations in memory
        if (conversations.size > MAX_CONVERSATIONS) {
            conversations.removeAt(0)
        }

        // Save to permanent storage
        saveConversationsToStorage()

        Log.d(TAG, "Added conversation: $message")
    }

    /**
     * Get recent context as a list of message strings (for LLM context)
     * This helps the AI understand conversation flow
     */
    fun getRecentContext(count: Int = 5): List<String> {
        return conversations
            .takeLast(count * 2)
            .map { it.message }
    }

    /**
     * Get recent conversations with full details (for UI display)
     */
    fun getRecentConversations(count: Int = 50): List<ConversationEntry> {
        return conversations.takeLast(count)
    }

    /**
     * Get all conversations (for full history display)
     */
    fun getAllConversations(): List<ConversationEntry> {
        return conversations.toList()
    }

    /**
     * Get the total number of conversations
     */
    fun getConversationCount(): Int {
        return conversations.size
    }

    /**
     * Get memory size (total number of messages)
     */
    fun getMemorySize(): Int = conversations.size

    /**
     * Get the last user message (without prefix)
     */
    fun getLastUserMessage(): String? {
        return conversations
            .findLast { it.type == ConversationType.USER_INPUT }
            ?.message
            ?.removePrefix("User: ")
    }

    /**
     * Get the last assistant response (without prefix)
     */
    fun getLastAssistantResponse(): String? {
        return conversations
            .findLast { it.type == ConversationType.ASSISTANT_RESPONSE }
            ?.message
            ?.removePrefix("Assistant: ")
    }

    /**
     * Search conversations by query string
     */
    fun searchConversations(query: String): List<ConversationEntry> {
        return conversations.filter {
            it.message.contains(query, ignoreCase = true)
        }
    }

    /**
     * Export all conversations as formatted text
     */
    fun exportConversations(): String {
        return conversations.joinToString("\n") {
            "[${it.timestamp}] ${it.type}: ${it.message}"
        }
    }

    /**
     * Clear all conversations from memory and storage
     */
    fun clearMemory() {
        conversations.clear()
        prefs.edit().remove(KEY_CONVERSATIONS).apply()
        Log.d(TAG, "Memory cleared from RAM and storage")
    }

    /**
     * Get conversation statistics
     */
    fun getStatistics(): ConversationStats {
        val userMessages = conversations.count { it.type == ConversationType.USER_INPUT }
        val assistantMessages = conversations.count { it.type == ConversationType.ASSISTANT_RESPONSE }
        val systemMessages = conversations.count { it.type == ConversationType.SYSTEM_MESSAGE }

        return ConversationStats(
            total = conversations.size,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            systemMessages = systemMessages,
            firstConversation = conversations.firstOrNull()?.timestamp,
            lastConversation = conversations.lastOrNull()?.timestamp
        )
    }

    data class ConversationStats(
        val total: Int,
        val userMessages: Int,
        val assistantMessages: Int,
        val systemMessages: Int,
        val firstConversation: String?,
        val lastConversation: String?
    )

    /**
     * Save conversations to permanent storage (SharedPreferences)
     */
    private fun saveConversationsToStorage() {
        try {
            val jsonArray = JSONArray()

            conversations.forEach { entry ->
                val jsonObject = JSONObject().apply {
                    put("message", entry.message)
                    put("timestamp", entry.timestamp)
                    put("type", entry.type.name)
                }
                jsonArray.put(jsonObject)
            }

            prefs.edit()
                .putString(KEY_CONVERSATIONS, jsonArray.toString())
                .apply()

            Log.d(TAG, "Saved ${conversations.size} conversations to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversations", e)
        }
    }

    /**
     * Load conversations from permanent storage
     */
    private fun loadConversationsFromStorage() {
        try {
            val jsonString = prefs.getString(KEY_CONVERSATIONS, null)

            if (jsonString != null) {
                val jsonArray = JSONArray(jsonString)
                conversations.clear()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val entry = ConversationEntry(
                        message = jsonObject.getString("message"),
                        timestamp = jsonObject.getString("timestamp"),
                        type = ConversationType.valueOf(jsonObject.getString("type"))
                    )
                    conversations.add(entry)
                }

                Log.d(TAG, "Loaded ${conversations.size} conversations from storage")
            } else {
                Log.d(TAG, "No saved conversations found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
        }
    }

    /**
     * Get conversation history for a specific date
     */
    fun getConversationsByDate(date: String): List<ConversationEntry> {
        return conversations.filter { it.timestamp.startsWith(date) }
    }

    /**
     * Get most frequently called contacts or used commands
     */
    fun getMostFrequentActions(limit: Int = 5): Map<String, Int> {
        val actionCounts = mutableMapOf<String, Int>()

        conversations.forEach { entry ->
            if (entry.type == ConversationType.USER_INPUT) {
                val message = entry.message.lowercase()
                when {
                    message.contains("call") -> {
                        val contact = message.removePrefix("user: call").trim()
                        actionCounts[contact] = actionCounts.getOrDefault(contact, 0) + 1
                    }
                    message.contains("open") -> {
                        val app = message.removePrefix("user: open").trim()
                        actionCounts[app] = actionCounts.getOrDefault(app, 0) + 1
                    }
                }
            }
        }

        return actionCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to it.value }
    }

    /**
     * Save user preferences (for making Groot smarter)
     */
    fun saveUserPreference(key: String, value: String) {
        try {
            val prefsJson = prefs.getString(KEY_USER_PREFERENCES, "{}")
            val jsonObject = JSONObject(prefsJson)
            jsonObject.put(key, value)

            prefs.edit()
                .putString(KEY_USER_PREFERENCES, jsonObject.toString())
                .apply()

            Log.d(TAG, "Saved user preference: $key = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user preference", e)
        }
    }

    /**
     * Get user preference
     */
    fun getUserPreference(key: String): String? {
        return try {
            val prefsJson = prefs.getString(KEY_USER_PREFERENCES, "{}")
            val jsonObject = JSONObject(prefsJson)
            if (jsonObject.has(key)) {
                jsonObject.getString(key)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user preference", e)
            null
        }
    }

    /**
     * Get storage size in bytes
     */
    fun getStorageSize(): Long {
        val conversationsSize = prefs.getString(KEY_CONVERSATIONS, "")?.length?.toLong() ?: 0L
        val preferencesSize = prefs.getString(KEY_USER_PREFERENCES, "")?.length?.toLong() ?: 0L
        return conversationsSize + preferencesSize
    }

    /**
     * Export conversations to JSON file (for backup)
     */
    fun exportToJson(): String {
        val mainObject = JSONObject()

        // Export conversations
        val conversationsArray = JSONArray()
        conversations.forEach { entry ->
            val jsonObject = JSONObject().apply {
                put("message", entry.message)
                put("timestamp", entry.timestamp)
                put("type", entry.type.name)
            }
            conversationsArray.put(jsonObject)
        }
        mainObject.put("conversations", conversationsArray)

        // Export preferences
        val prefsJson = prefs.getString(KEY_USER_PREFERENCES, "{}")
        mainObject.put("preferences", JSONObject(prefsJson))

        // Export metadata
        val metadata = JSONObject().apply {
            put("total_conversations", conversations.size)
            put("export_date", dateFormatter.format(Date()))
            put("version", "1.0")
        }
        mainObject.put("metadata", metadata)

        return mainObject.toString(2) // Pretty print
    }
}