package com.example.groot

object LanguageDetector {

    private val hinglishKeywords = setOf(
        // Greetings
        "namaste", "namaskar", "pranam",

        // Questions
        "kaise", "kaisa", "kya", "kab", "kahan", "kyun", "kon","kiska","kiski","kisne","ki","kri",

        // Actions
        "karo", "karna", "krdo", "bhejo", "batao", "dikho", "suno",
        "bolo", "chalo", "aao", "jao", "dekho",

        // Common words
        "haan", "nahi", "achha", "thik", "theek", "bas", "aur",
        "mujhe", "tumhe", "aapko", "mere", "tumhare", "apke","mere","mera",

        // Polite
        "dhanyavad", "shukriya", "maaf", "sorry","dhanyvad",

        // Time/Day
        "aaj", "kal", "parso", "abhi", "baad","samay","din","konsa","antim","antim", "aakhri", "पिछला", "अंतिम",

        // SMS/Call specific
        "message", "msg", "call", "phone" ,"ko"
    )

    /**
     * Detect if user intent is Hindi/Hinglish
     * Returns true for both Devanagari and romanized Hindi
     */
    fun isHindiIntent(text: String): Boolean {
        val lowerText = text.lowercase()

        // Check 1: Devanagari script
        if (text.matches(Regex(".*[\\u0900-\\u097F].*"))) {
            return true
        }

        // Check 2: Hinglish keywords (need at least 2 matches for reliability)
        val matchCount = hinglishKeywords.count { lowerText.contains(it) }
        if (matchCount >= 2) {
            return true
        }

        // Check 3: Single strong keyword
        val strongKeywords = setOf("namaste", "dhanyavad", "shukriya", "kaise", "kya","antim", "पिछली")
        if (strongKeywords.any { lowerText.contains(it) }) {
            return true
        }

        return false
    }
}