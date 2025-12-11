package com.example.groot.birthday

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Central manager for birthday wishes system with multi-channel support
 */
class BirthdayManager(private val context: Context) {

    companion object {
        private const val TAG = "BirthdayManager"
        private const val PREFS_NAME = "birthday_prefs"
        private const val KEY_LAST_SCAN = "last_scan_timestamp"
        private const val KEY_AUTO_SCAN_ENABLED = "auto_scan_enabled"

        const val ACTION_BIRTHDAY_CHECK = "com.example.groot.ACTION_BIRTHDAY_CHECK"
        const val ACTION_MORNING_REMINDER = "com.example.groot.ACTION_MORNING_REMINDER"
    }

    private val database = BirthdayDatabase.getDatabase(context)
    private val contactDao = database.contactDao()
    private val contactScanner = ContactScanner(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== INITIALIZATION ====================

    /**
     * Initialize birthday system
     * Call this from GrootService.onCreate()
     */
    suspend fun initialize() {
        try {
            Log.d(TAG, "🎂 Initializing Birthday Manager...")

            // Check if we need to scan contacts
            val lastScan = prefs.getLong(KEY_LAST_SCAN, 0)
            val daysSinceLastScan = (System.currentTimeMillis() - lastScan) / (1000 * 60 * 60 * 24)

            if (daysSinceLastScan > 7 || lastScan == 0L) {
                Log.d(TAG, "📱 Last scan was $daysSinceLastScan days ago. Scanning contacts...")
                scanAndSaveContacts()
            }

            // Schedule daily midnight check
            scheduleMidnightBirthdayCheck()

            Log.d(TAG, "✅ Birthday Manager initialized")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Birthday Manager", e)
        }
    }

    // ==================== CONTACT SCANNING ====================

    /**
     * Scan phone contacts and save to database
     */
    suspend fun scanAndSaveContacts(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Scanning contacts...")

            val contacts = contactScanner.scanContacts()

            if (contacts.isNotEmpty()) {
                contactDao.insertContacts(contacts)
                scheduleMidnightBirthdayCheck()
                prefs.edit().putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()

                val withBirthdays = contacts.count { it.dateOfBirth != null }
                Log.d(TAG, "✅ Saved ${contacts.size} contacts ($withBirthdays with birthdays)")

                return@withContext contacts.size
            }

            0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning contacts", e)
            0
        }
    }

    /**
     * Add/Update birthday for a specific contact manually
     */
    suspend fun setContactBirthday(
        contactName: String,
        dateOfBirth: String // Format: "dd/MM/yyyy"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Search existing contacts
            val contacts = contactDao.searchContactsByName(contactName)

            if (contacts.isNotEmpty()) {
                val contact = contacts.first()
                contactDao.updateDateOfBirth(contact.contactId, dateOfBirth)
                scheduleMidnightBirthdayCheck()
                Log.d(TAG, "✅ Updated birthday for ${contact.name}: $dateOfBirth")
                return@withContext true
            } else {
                // Scan from phone contacts
                val scannedContact = contactScanner.scanContactByName(contactName)
                if (scannedContact != null) {
                    val updatedContact = scannedContact.copy(dateOfBirth = dateOfBirth)
                    contactDao.insertContact(updatedContact)
                    scheduleMidnightBirthdayCheck()
                    Log.d(TAG, "✅ Added new contact with birthday: ${updatedContact.name}")
                    return@withContext true
                }
            }

            Log.w(TAG, "⚠️ Contact not found: $contactName")
            false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting birthday", e)
            false
        }
    }

    // ==================== BIRTHDAY CHECKING ====================

    /**
     * Check for birthdays today and send wishes
     * Called at midnight
     */
    suspend fun checkAndSendBirthdayWishes(): List<Contact> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎂 Checking birthdays for today...")

            val potentialBirthdays = contactDao.getPotentialBirthdaysToday()
            val todayBirthdays = potentialBirthdays.filter {
                it.isBirthdayToday() && !it.alreadyWishedThisYear()
            }

            Log.d(TAG, "🎉 Found ${todayBirthdays.size} birthdays today")

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)

            todayBirthdays.forEach { contact ->
                // Update last wished year
                contactDao.updateLastWishedYear(contact.contactId, currentYear)
                Log.d(TAG, "📅 Marked ${contact.name} as wished for $currentYear")
            }

            todayBirthdays

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking birthdays", e)
            emptyList()
        }
    }

    // ==================== ALARM SCHEDULING ====================

    /**
     * Schedule daily midnight birthday check
     */
    fun scheduleMidnightBirthdayCheck() {
    try {
        // ✅ STEP 1: Check exact alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "❌ CRITICAL: Exact Alarm Permission NOT granted!")
                Log.e(TAG, "User needs to enable 'Alarms & reminders' permission")
                
                // ⚠️ Optional: Show notification to user
                showExactAlarmPermissionNotification()
                return
            } else {
                Log.d(TAG, "✅ Exact Alarm Permission granted")
            }
        }

        // ✅ STEP 2: Calculate next midnight
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            
            // Set to midnight (00:00:00.000)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 10) // ✅ 10 seconds after midnight for stability
            set(Calendar.MILLISECOND, 0)

            // If time has passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // ✅ STEP 3: Create Intent
        val intent = Intent(context, BirthdayReceiver::class.java).apply {
            action = ACTION_BIRTHDAY_CHECK
            // Add extra data for debugging
            putExtra("scheduled_time", calendar.timeInMillis)
            putExtra("scheduled_date", calendar.time.toString())
        }

        // ✅ STEP 4: Create PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ STEP 5: Cancel any existing alarm
        try {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "🗑️ Cancelled existing alarm")
        } catch (e: Exception) {
            Log.w(TAG, "No existing alarm to cancel")
        }

        // ✅ STEP 6: Schedule new alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for better reliability
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "⏰ setExactAndAllowWhileIdle() used")
            } else {
                // Fallback for older Android versions
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "⏰ setExact() used (old Android)")
            }

            // ✅ STEP 7: Log success
            val now = Calendar.getInstance()
            val hoursUntil = (calendar.timeInMillis - now.timeInMillis) / (1000 * 60 * 60)
            
            Log.d(TAG, "═══════════════════════════════")
            Log.d(TAG, "✅ MIDNIGHT ALARM SCHEDULED!")
            Log.d(TAG, "Current Time: ${now.time}")
            Log.d(TAG, "Alarm Time: ${calendar.time}")
            Log.d(TAG, "Hours until alarm: $hoursUntil")
            Log.d(TAG, "Request Code: 9999")
            Log.d(TAG, "═══════════════════════════════")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException scheduling alarm", e)
            Log.e(TAG, "This happens if SCHEDULE_EXACT_ALARM permission denied")
        }

    } catch (e: Exception) {
        Log.e(TAG, "❌ FATAL: Error scheduling midnight check", e)
        e.printStackTrace()
    }
}

/**
 * ✅ NEW: Show notification if exact alarm permission needed
 */
private fun showExactAlarmPermissionNotification() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Create intent to open alarm settings
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            )
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(
                context,
                "groot_birthday_channel"
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⚠️ Birthday Feature Needs Permission")
                .setContentText("Enable 'Alarms & reminders' for auto birthday wishes")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            
            try {
                notificationManager.notify(12345, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "Notification permission denied")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error showing permission notification", e)
    }
}

    /**
     * Schedule morning reminder for specific birthday contact
     */
    fun scheduleMorningReminder(contact: Contact) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms")
                    return
                }
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8) // 8 AM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)

                // If 7 AM has passed, schedule for tomorrow
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val intent = Intent(context, BirthdayReceiver::class.java).apply {
                action = ACTION_MORNING_REMINDER
                putExtra("contact_name", contact.name)
                putExtra("phone_number", contact.phoneNumber)
                putExtra("contact_email", contact.email)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                contact.contactId.hashCode() + 10000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "⏰ Morning reminder scheduled for ${contact.name} at 7 AM")
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException scheduling morning reminder", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling morning reminder", e)
        }
    }

    // ==================== QUERY METHODS ====================

    /**
     * Get all contacts (including those without birthdays)
     */
    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        contactDao.getAllContacts()
    }

    /**
     * Get all contacts with birthdays
     */
    suspend fun getAllBirthdays(): List<Contact> = withContext(Dispatchers.IO) {
        contactDao.getContactsWithBirthday()
    }

    /**
     * Get upcoming birthdays (next 30 days)
     */
    suspend fun getUpcomingBirthdays(): List<Contact> = withContext(Dispatchers.IO) {
        val allContacts = contactDao.getUpcomingBirthdays()
        val today = Calendar.getInstance()

        allContacts.filter { contact ->
            val daysUntil = getDaysUntilBirthday(contact, today)
            daysUntil in 0..30
        }.sortedBy { getDaysUntilBirthday(it, today) }
    }

    /**
     * Calculate days until birthday
     */
    private fun getDaysUntilBirthday(contact: Contact, today: Calendar): Int {
        if (contact.dateOfBirth == null) return Int.MAX_VALUE

        val parts = contact.dateOfBirth.split("/")
        if (parts.size != 3) return Int.MAX_VALUE

        val birthDay = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val birthMonth = parts[1].toIntOrNull() ?: return Int.MAX_VALUE

        val nextBirthday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, birthDay)
            set(Calendar.MONTH, birthMonth - 1)
            set(Calendar.YEAR, today.get(Calendar.YEAR))

            // If birthday has passed this year, check next year
            if (timeInMillis < today.timeInMillis) {
                add(Calendar.YEAR, 1)
            }
        }

        val diff = nextBirthday.timeInMillis - today.timeInMillis
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * Get contact by name
     */
    suspend fun getContactByName(name: String): Contact? = withContext(Dispatchers.IO) {
        contactDao.searchContactsByName(name).firstOrNull()
    }

    /**
     * Get contact by ID
     */
    suspend fun getContactById(contactId: String): Contact? = withContext(Dispatchers.IO) {
        try {
            contactDao.getAllContacts().find { it.contactId == contactId }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact by ID", e)
            null
        }
    }

    /**
     * Get statistics
     */
    suspend fun getStats(): BirthdayStats = withContext(Dispatchers.IO) {
        val totalContacts = contactDao.getContactCount()
        val withBirthdays = contactDao.getContactsWithBirthdayCount()
        val upcomingCount = getUpcomingBirthdays().size

        BirthdayStats(totalContacts, withBirthdays, upcomingCount)
    }

    data class BirthdayStats(
        val totalContacts: Int,
        val contactsWithBirthdays: Int,
        val upcomingBirthdays: Int
    )

    // ==================== CONTACT MANAGEMENT ====================

    /**
     * Add new contact manually
     */
    suspend fun addContact(contact: Contact): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check for duplicates
            val existingByPhone = contactDao.getAllContacts().find {
                it.phoneNumber == contact.phoneNumber
            }

            if (existingByPhone != null) {
                Log.w(TAG, "⚠️ Contact with this number already exists")
                return@withContext false
            }

            contactDao.insertContact(contact)
            scheduleMidnightBirthdayCheck()
            Log.d(TAG, "✅ Contact added: ${contact.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding contact", e)
            false
        }
    }

    /**
     * Delete contact from database
     */
    suspend fun deleteContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            contactDao.deleteContactById(contactId)
            Log.d(TAG, "✅ Contact deleted: $contactId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting contact", e)
            false
        }
    }

    /**
     * Update contact email
     */
    suspend fun updateContactEmail(contactId: String, email: String) = withContext(Dispatchers.IO) {
        try {
            contactDao.updateEmail(contactId, email)
            Log.d(TAG, "✅ Email updated for contact: $contactId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating email", e)
        }
    }

    /**
     * Update wish preferences (SMS/Email)
     */
    suspend fun updateWishPreferences(
        contactId: String,
        smsEnabled: Boolean,
        emailEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            contactDao.updateWishViaSMS(contactId, smsEnabled)
            contactDao.updateWishViaEmail(contactId, emailEnabled)
            Log.d(TAG, "✅ Wish preferences updated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating preferences", e)
        }
    }
    // Add to BirthdayManager.kt

    /**
     * Check if daily alarm is already scheduled
     */
    fun isAlarmScheduled(): Boolean {
    return try {
        val intent = Intent(context, BirthdayReceiver::class.java).apply {
            action = ACTION_BIRTHDAY_CHECK
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        val isScheduled = pendingIntent != null

        Log.d(TAG, if (isScheduled) "✅ Alarm IS scheduled" else "❌ Alarm NOT scheduled")
        
        // Also check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = alarmManager.canScheduleExactAlarms()
            Log.d(TAG, if (hasPermission) "✅ Permission granted" else "❌ Permission denied")
            
            return isScheduled && hasPermission
        }
        
        isScheduled

    } catch (e: Exception) {
        Log.e(TAG, "Error checking alarm status", e)
        false
    }
}

}

