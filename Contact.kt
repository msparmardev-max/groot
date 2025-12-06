package com.example.groot.birthday

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Contact entity for Room Database
 * Stores contact information with birthday
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val contactId: String,           // Unique contact ID from phone

    val name: String,                 // Contact name
    val phoneNumber: String,          // Primary phone number
    val email: String? = null,        // ✅ NEW: Email address (optional)
    val dateOfBirth: String?,         // Format: "dd/MM/yyyy" or null if not set
    val lastWishedYear: Int? = null,  // Last year we sent wishes (prevent duplicates)
    val isAutoWishEnabled: Boolean = true, // User can disable per contact
    val customMessage: String? = null, // Optional custom birthday message
    val addedTimestamp: Long = System.currentTimeMillis(), // When this contact was added
    val wishViaEmail: Boolean = false, // ✅ NEW: Send birthday wish via email
    val wishViaSMS: Boolean = true     // ✅ NEW: Send birthday wish via SMS
) {
    /**
     * Check if today is this contact's birthday
     */
    fun isBirthdayToday(): Boolean {
        if (dateOfBirth == null) return false

        val today = java.util.Calendar.getInstance()
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(java.util.Calendar.MONTH) + 1

        val parts = dateOfBirth.split("/")
        if (parts.size != 3) return false

        val birthDay = parts[0].toIntOrNull() ?: return false
        val birthMonth = parts[1].toIntOrNull() ?: return false

        return birthDay == todayDay && birthMonth == todayMonth
    }

    /**
     * Check if we already sent wishes this year
     */
    fun alreadyWishedThisYear(): Boolean {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return lastWishedYear == currentYear
    }

    /**
     * Get age (if birth year is available)
     */
    fun getAge(): Int? {
        if (dateOfBirth == null) return null
        val parts = dateOfBirth.split("/")
        if (parts.size != 3) return null

        val birthYear = parts[2].toIntOrNull() ?: return null
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        return currentYear - birthYear
    }

    /**
     * Format birthday for display
     */
    fun getFormattedBirthday(): String? {
        if (dateOfBirth == null) return null
        val parts = dateOfBirth.split("/")
        if (parts.size != 3) return null

        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        val day = parts[0]
        val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return null
        val year = parts[2]

        return "$day ${months.getOrNull(monthIndex)} $year"
    }
}