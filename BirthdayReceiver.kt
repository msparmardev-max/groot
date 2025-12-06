package com.example.groot.birthday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.groot.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver to handle birthday alarms and send multi-channel wishes
 */
class BirthdayReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BirthdayReceiver"
        private const val NOTIFICATION_CHANNEL_ID = "groot_birthday_channel" // ✅ FIXED
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📡 Received broadcast: ${intent.action}")

        when (intent.action) {
            BirthdayManager.ACTION_BIRTHDAY_CHECK -> {
                handleMidnightBirthdayCheck(context)
            }

            BirthdayManager.ACTION_MORNING_REMINDER -> {
                val contactName = intent.getStringExtra("contact_name")
                val phoneNumber = intent.getStringExtra("phone_number")
                val contactEmail = intent.getStringExtra("contact_email")

                if (contactName != null && phoneNumber != null) {
                    handleMorningReminder(context, contactName, phoneNumber, contactEmail)
                }
            }

            "com.example.groot.SEND_BIRTHDAY_WISH" -> {
                val contactId = intent.getStringExtra("contact_id")
                if (contactId != null) {
                    handleSendBirthdayWish(context, contactId)
                }
            }
        }
    }

    /**
     * Handle midnight birthday check - Send wishes via SMS/Email
     */
    private fun handleMidnightBirthdayCheck(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "🎂 Running midnight birthday check...")

                val birthdayManager = BirthdayManager(context)
                val communicationManager = CommunicationManager(context)

                // Get today's birthdays
                val birthdays = birthdayManager.checkAndSendBirthdayWishes()

                if (birthdays.isNotEmpty()) {
                    Log.d(TAG, "🎉 Found ${birthdays.size} birthdays today")

                    birthdays.forEach { contact ->
                        // Generate message
                        val message = communicationManager.generateBirthdayMessage(contact)

                        // Send via SMS and/or Email
                        val result = communicationManager.sendBirthdayWish(
                            contact = contact,
                            message = message,
                            viaSMS = contact.wishViaSMS && contact.phoneNumber.isNotEmpty(),
                            viaEmail = contact.wishViaEmail && !contact.email.isNullOrEmpty()
                        )

                        if (result.anySuccess) {
                            Log.d(TAG, "✅ Birthday wish sent to: ${contact.name}")

                            // Schedule morning reminder
                            birthdayManager.scheduleMorningReminder(contact)
                        } else {
                            Log.e(TAG, "❌ Failed to send wish to: ${contact.name}")
                        }
                    }
                } else {
                    Log.d(TAG, "📭 No birthdays today")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in midnight check", e)
            }
        }
    }

    /**
     * Handle morning reminder (7 AM) - Voice + Notification
     */
    private fun handleMorningReminder(
        context: Context,
        contactName: String,
        phoneNumber: String,
        contactEmail: String?
    ) {
        scope.launch {
            try {
                Log.d(TAG, "🌅 Morning reminder for: $contactName")

                // Initialize TTS
                val ttsManager = TTSManager(context)
                ttsManager.initialize()

                // Detect language
                val isHindi = contactName.matches(Regex(".*[\\u0900-\\u097F].*"))

                // Create reminder message
                val message = if (isHindi) {
                    "सर, आज $contactName का जन्मदिन है। मैंने रात को उन्हें शुभकामना संदेश भेज दिया है। क्या आप उन्हें कॉल करना चाहेंगे?"
                } else {
                    "Sir, today is ${contactName}'s birthday. I've already sent them a wish at midnight. Would you like to call them?"
                }

                // ✅ FIXED: Remove isHindi parameter
                ttsManager.speak(
                    text = message,
                    emotion = "friendly",
                    callback = object : TTSManager.TTSCallback {
                        override fun onStart() {
                            Log.d(TAG, "🔊 Speaking morning reminder")
                        }

                        override fun onDone() {
                            Log.d(TAG, "✅ Morning reminder complete")
                            ttsManager.shutdown()
                            openGrootForResponse(context, contactName, phoneNumber)
                        }

                        override fun onError() {
                            Log.e(TAG, "❌ TTS error in morning reminder")
                            ttsManager.shutdown()
                        }
                    },
                    isHindi = true
                )

                // Show notification
                showBirthdayNotification(context, contactName, phoneNumber, isHindi)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in morning reminder", e)
            }
        }
    }

    /**
     * Send birthday wish for specific contact
     */
    private fun handleSendBirthdayWish(context: Context, contactId: String) {
        scope.launch {
            try {
                val birthdayManager = BirthdayManager(context)
                val communicationManager = CommunicationManager(context)

                // Get contact
                val contact = birthdayManager.getContactById(contactId)

                if (contact != null) {
                    val message = communicationManager.generateBirthdayMessage(contact)

                    val result = communicationManager.sendBirthdayWish(
                        contact = contact,
                        message = message,
                        viaSMS = contact.wishViaSMS,
                        viaEmail = contact.wishViaEmail
                    )

                    Log.d(TAG, "Wish sent: SMS=${result.smsSuccess}, Email=${result.emailSuccess}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending wish", e)
            }
        }
    }

    /**
     * Show birthday notification
     */
    private fun showBirthdayNotification(
        context: Context,
        contactName: String,
        phoneNumber: String,
        isHindi: Boolean
    ) {
        try {
            val title = if (isHindi) "🎂 जन्मदिन का रिमाइंडर" else "🎂 Birthday Reminder"
            val message = if (isHindi) {
                "आज $contactName का जन्मदिन है। क्या आप उन्हें कॉल करना चाहेंगे?"
            } else {
                "Today is ${contactName}'s birthday. Would you like to call them?"
            }

            // Create call intent
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val callPendingIntent = android.app.PendingIntent.getActivity(
                context,
                contactName.hashCode(),
                callIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // ✅ FIXED: Proper notification builder
            val notificationBuilder = androidx.core.app.NotificationCompat.Builder(
                context,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info) // ✅ FIXED: Use valid icon
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_call,
                    if (isHindi) "कॉल करें" else "Call",
                    callPendingIntent
                )

            // Create notification channel for Android 8.0+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Birthday Reminders",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for birthday reminders"
                    enableVibration(true)
                    enableLights(true)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                notificationManager.createNotificationChannel(channel)
            }

            // Show notification
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            try {
                notificationManager.notify(contactName.hashCode(), notificationBuilder.build())
                Log.d(TAG, "✅ Birthday notification shown")
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Notification permission denied", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification", e)
        }
    }

    /**
     * Open Groot app for user response
     */
    private fun openGrootForResponse(
        context: Context,
        contactName: String,
        phoneNumber: String
    ) {
        try {
            // Store state
            val prefs = context.getSharedPreferences("birthday_state", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("pending_birthday_call_name", contactName)
                putString("pending_birthday_call_number", phoneNumber)
                putLong("pending_birthday_call_time", System.currentTimeMillis())
                apply()
            }

            // Launch MainActivity
            val intent = Intent(context, com.example.groot.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("birthday_reminder", true)
                putExtra("contact_name", contactName)
                putExtra("phone_number", phoneNumber)
            }

            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening app", e)
        }
    }
}