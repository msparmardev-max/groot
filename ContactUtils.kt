package com.example.groot

import android.util.Log
import java.util.Locale
/**
 * Utility helpers for extracting/normalizing contact names and phone numbers from
 * free-form user commands (supports Hindi/Devanagari digits, Hinglish, English).
 *
 * Usage examples:
 * ```kotlin
 * val c = ContactUtils.parseContactFromText("save contact 987654321 Prem Ramesh")
 * // c.name == "Prem Ramesh", c.phone == "+91987654321"
 * val target = ContactUtils.formatTarget(c) // "Prem Ramesh:+91987654321"
 * ```
 */
object ContactUtils {

    data class ContactData(val name: String = "", val phone: String = "")

    // Map Devanagari digits to Latin digits
    private val devanagariMap = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    /** Convert Devanagari digits in the input to Latin digits so regexes can match. */
    fun devanagariToLatinDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) sb.append(devanagariMap[ch] ?: ch)
        return sb.toString()
    }

    /**
     * Extract a phone-like token from free-form text.
     * Returns E.164-like string for 10-digit Indian numbers (prefixes with +91).
     * Returns empty string when none found.
     */
    fun extractPhoneNumber(command: String): String {
        val normalized = devanagariToLatinDigits(command)

        // Look for grouped patterns like +91 98765-43210 or 98765 43210
        val groupedPattern = Regex("(?:\\+?\\d[\\d\\s-]{6,20}\\d)")
        groupedPattern.find(normalized)?.let { match ->
            var num = match.value.replace(Regex("[^+0-9]"), "")
            val digitsOnly = num.replace("+", "")
            num = when {
                !num.startsWith("+") && digitsOnly.length == 10 -> "+91$digitsOnly"
                !num.startsWith("+") && digitsOnly.length in 7..13 -> digitsOnly
                else -> num
            }
            return num
        }

        // Fallback: contiguous digit run (7-13 digits)
        Regex("\\d{7,13}").find(normalized)?.value?.let { digits ->
            return if (digits.length == 10) "+91$digits" else digits
        }

        return ""
    }

    // Common words to ignore while extracting name
    private val stopwords = listOf(
        "save", "add", "contact", "number", "no", "namber",
        "सेव", "संपर्क", "जोड़ो", "जोड़ना", "करो", "करे", "को", "as",
        "mein", "में", "please", "pls", "कृपया", "नाम", "का", "का नंबर"
    )

    private val stopwordsRegex = Regex("\\b(${stopwords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    /**
     * Extract a probable contact name from free-form text. Optionally provide phoneHint
     * to remove it from the input before extracting the name.
     */
    fun extractContactName(command: String, phoneHint: String? = null): String {
        var orig = command.trim()

        // Remove phone-like clusters to make name extraction easier
        orig = orig.replace(Regex("(?:\\+?\\d[\\d\\s-]{6,20}\\d)"), " ")
        orig = orig.replace(Regex("\\d{7,13}"), " ")

        // Remove common stopwords and punctuation
        var cleaned = orig.replace(stopwordsRegex, " ")
        cleaned = cleaned.replace(Regex("[:,]"), " ").replace(Regex("\\s+"), " ").trim()

        if (cleaned.isEmpty()) return ""

        // Try ordered patterns (Hindi + English + Hinglish variants)
        val patterns = listOf(
            Regex("(?:ko|को)\\s+([\\p{L} ]{1,60})\\s+(?:ke naam se|के नाम से|नाम से|naam se)", RegexOption.IGNORE_CASE),
            Regex("([\\p{L} ]{1,60})\\s+(?:ke naam se|के नाम से|नाम से|naam se)", RegexOption.IGNORE_CASE),
            Regex("(?:save contact|add contact|contact save|save)\\s+([\\p{L} ]{1,60})", RegexOption.IGNORE_CASE),
            Regex("(?:add|save)\\s+([\\p{L} ]{1,60})\\s+(?:as|को|का)", RegexOption.IGNORE_CASE),
            Regex("([\\p{L} ]{1,60})\\s+(?:ka number|का नंबर|का नम्बर|का नंबर)", RegexOption.IGNORE_CASE),
            Regex("(?:name|नाम)\\s*[:]?\\s*([\\p{L} ]{1,60})", RegexOption.IGNORE_CASE)
        )

        for (pat in patterns) {
            val m = pat.find(cleaned)
            if (m != null && m.groupValues.size >= 2) {
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotEmpty()) return normalizeName(candidate)
            }
        }

        // Fallback: take last up-to-3 words
        val tokens = cleaned.replace(Regex("[^\\p{L} ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            val last = tokens.takeLast(3).joinToString(" ").trim()
            if (last.isNotEmpty()) return normalizeName(last)
        }

        return ""
    }

    private fun normalizeName(raw: String): String {
        return raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }.trim()
    }

    /** Parse both name and phone in one call. */
    fun parseContactFromText(command: String): ContactData {
        val phone = extractPhoneNumber(command)
        val name = extractContactName(command, phone)
        return ContactData(name = name, phone = phone)
    }

    /** Utility to build target string consumed by TaskAutomationManager.handleAddContact */
    fun formatTarget(contact: ContactData): String = "${'$'}{contact.name}:${'$'}{contact.phone}"

    /**
     * Clean contact name for better matching
     */
    fun cleanContactName(name: String): String {
        return name.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }
}
