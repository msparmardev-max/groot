//package com.example.groot.birthday
//
//import android.content.Context
//import android.os.Build
//import android.util.Log
//import androidx.annotation.RequiresApi
//import com.example.groot.TaskAutomationManager
//import com.example.groot.email.EmailManager
//import com.example.groot.user.EmailPreferences
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import android.telephony.SmsManager
//
///**
// * Handles multi-channel communication (SMS, Email) for birthday wishes
// * Uses existing EmailManager and TaskAutomationManager
// */
//class CommunicationManager(private val context: Context) {
//
//    companion object {
//        private const val TAG = "CommunicationManager"
//    }
//
//    // ✅ Use existing managers
//    private val taskManager = TaskAutomationManager(context)
//    private val emailManager = EmailManager(context)
//
//    init {
//        // ✅ Load email credentials from EmailPreferences
//        val emailPrefs = EmailPreferences(context)
//        if (emailPrefs.isEmailConfigured()) {
//            val senderEmail = emailPrefs.getSenderEmail()
//            val senderPassword = emailPrefs.getSenderPassword()
//            if (senderEmail != null && senderPassword != null) {
//                emailManager.setupEmailCredentials(senderEmail, senderPassword)
//                Log.d(TAG, "✅ Email credentials loaded")
//            }
//        } else {
//            Log.w(TAG, "⚠️ Email credentials not configured")
//        }
//    }
//
//    /**
//     * Send birthday wish via multiple channels
//     */
//    @RequiresApi(Build.VERSION_CODES.O)
//    suspend fun sendBirthdayWish(
//        contact: Contact,
//        message: String,
//        viaSMS: Boolean = true,
//        viaEmail: Boolean = false
//    ): Result = withContext(Dispatchers.IO) {
//        var smsSuccess = false
//        var emailSuccess = false
//        val errors = mutableListOf<String>()
//
//        try {
//            // ✅ Send SMS using TaskAutomationManager
//            if (viaSMS && contact.phoneNumber.isNotEmpty()) {
//                smsSuccess = sendSMS(contact.phoneNumber, message)
//                if (!smsSuccess) {
//                    errors.add("SMS failed")
//                }
//            }
//
//            // ✅ Send Email using EmailManager
//            if (viaEmail && !contact.email.isNullOrEmpty()) {
//                emailSuccess = sendEmail(
//                    recipientEmail = contact.email!!,
//                    recipientName = contact.name,
//                    message = message
//                )
//                if (!emailSuccess) {
//                    errors.add("Email failed")
//                }
//            }
//
//            Result(
//                smsSuccess = smsSuccess,
//                emailSuccess = emailSuccess,
//                errors = errors
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error sending birthday wish", e)
//            Result(
//                smsSuccess = false,
//                emailSuccess = false,
//                errors = listOf(e.message ?: "Unknown error")
//            )
//        }
//    }
//
//    /**
//     * ✅ Send SMS using TaskAutomationManager
//     */
//    private suspend fun sendSMS(phoneNumber: String, message: String): Boolean {
//        return withContext(Dispatchers.IO) {
//            try {
//                // ✅ Sidha Android ka SmsManager use karo
//                val smsManager = SmsManager.getDefault()
//
//                smsManager.sendTextMessage(
//                    phoneNumber,  // Seedha phone number
//                    null,         // Default SMS center
//                    message,      // Birthday wish message
//                    null,         // No sent notification
//                    null          // No delivery notification
//                )
//
//                Log.d(TAG, "✅ SMS sent successfully")
//                true  // ✅ Success!
//
//            } catch (e: Exception) {
//                Log.e(TAG, "❌ SMS failed: ${e.message}")
//                false  // ❌ Failed!
//            }
//        }
//    }
//
//    /**
//     * ✅ Send Email using EmailManager
//     */
//    private suspend fun sendEmail(
//        recipientEmail: String,
//        recipientName: String,
//        message: String
//    ): Boolean = withContext(Dispatchers.IO) {
//        try {
//            val subject = "🎉 Happy Birthday $recipientName!"
//
//            // ✅ Use EmailManager's sendEmail function
//            val success = emailManager.sendEmail(
//                to = recipientEmail,
//                subject = subject,
//                body = message
//            )
//
//            if (success) {
//                Log.d(TAG, "✅ Email sent to $recipientEmail via EmailManager")
//            } else {
//                Log.e(TAG, "❌ Email failed for $recipientEmail")
//            }
//
//            success
//
//        } catch (e: Exception) {
//            Log.e(TAG, "❌ Email error: ${e.message}", e)
//            false
//        }
//    }
//
//    /**
//     * Generate birthday message
//     */
//    fun generateBirthdayMessage(contact: Contact): String {
//        val age = contact.getAge()
//
//        return if (contact.customMessage != null) {
//            contact.customMessage!!
//        } else if (age != null && age > 0 && age < 120) {
//            """
//            🎉 Happy ${age}th Birthday ${contact.name}! 🎂
//
//            Wishing you a wonderful day filled with joy and happiness! 🎈
//
//            May this year bring you success, good health, and lots of smiles! 😊
//
//            Best wishes,
//            Sent via Groot 🤖
//            """.trimIndent()
//        } else {
//            """
//            🎉 Happy Birthday ${contact.name}! 🎂
//
//            Wishing you a fantastic day ahead! 🎈
//
//            May all your dreams come true this year! ✨
//
//            Best wishes,
//            Sent via Groot 🤖
//            """.trimIndent()
//        }
//    }
//
//    /**
//     * Generate Hindi birthday message
//     */
//    fun generateHindiBirthdayMessage(contact: Contact): String {
//        val age = contact.getAge()
//
//        return if (age != null && age > 0 && age < 120) {
//            """
//            🎉 ${contact.name} को ${age}वें जन्मदिन की हार्दिक शुभकामनाएं! 🎂
//
//            आपका दिन खुशियों से भरा हो! 🎈
//
//            आपको अच्छी सेहत और सफलता मिले! 😊
//
//            शुभकामनाएं,
//            Groot 🤖 द्वारा भेजा गया
//            """.trimIndent()
//        } else {
//            """
//            🎉 ${contact.name} को जन्मदिन की हार्दिक शुभकामनाएं! 🎂
//
//            आपका दिन मंगलमय हो! 🎈
//
//            आपकी सभी मनोकामनाएं पूर्ण हों! ✨
//
//            शुभकामनाएं,
//            Groot 🤖 द्वारा भेजा गया
//            """.trimIndent()
//        }
//    }
//
//    /**
//     * Generate custom message from template
//     */
//    fun generateCustomMessage(
//        contact: Contact,
//        template: String
//    ): String {
//        val age = contact.getAge()
//
//        return template
//            .replace("{name}", contact.name)
//            .replace("{age}", age?.toString() ?: "")
//            .replace("{phone}", contact.phoneNumber)
//    }
//
//    /**
//     * Result class
//     */
//    data class Result(
//        val smsSuccess: Boolean,
//        val emailSuccess: Boolean,
//        val errors: List<String> = emptyList()
//    ) {
//        val anySuccess: Boolean
//            get() = smsSuccess || emailSuccess
//
//        val bothSuccess: Boolean
//            get() = smsSuccess && emailSuccess
//    }
//}
package com.example.groot.birthday

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.groot.email.EmailManager
import com.example.groot.user.EmailPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Handles multi-channel communication (SMS, Email) for birthday wishes
 * Uses callback-based SMS sending like TaskAutomationManager
 */
class CommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "CommunicationManager"
    }

    private val emailManager = EmailManager(context)

    // ✅ Callback for SMS status
    private var onSMSStatusCallback: ((Boolean, String) -> Unit)? = null

    init {
        // Load email credentials
        val emailPrefs = EmailPreferences(context)
        if (emailPrefs.isEmailConfigured()) {
            val senderEmail = emailPrefs.getSenderEmail()
            val senderPassword = emailPrefs.getSenderPassword()
            if (senderEmail != null && senderPassword != null) {
                emailManager.setupEmailCredentials(senderEmail, senderPassword)
                Log.d(TAG, "✅ Email credentials loaded")
            }
        } else {
            Log.w(TAG, "⚠️ Email not configured")
        }
    }

    /**
     * Send birthday wish via multiple channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sendBirthdayWish(
        contact: Contact,
        message: String,
        viaSMS: Boolean = true,
        viaEmail: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        var smsSuccess = false
        var emailSuccess = false
        val errors = mutableListOf<String>()

        try {
            // ✅ Send SMS with callback support
            if (viaSMS && contact.phoneNumber.isNotEmpty()) {
                smsSuccess = sendSMSWithCallback(contact.phoneNumber, message)
                if (!smsSuccess) {
                    errors.add("SMS failed")
                }
            }

            // ✅ Send Email using EmailManager
            if (viaEmail && !contact.email.isNullOrEmpty()) {
                emailSuccess = sendEmail(
                    recipientEmail = contact.email!!,
                    recipientName = contact.name,
                    message = message
                )
                if (!emailSuccess) {
                    errors.add("Email failed")
                }
            }

            Result(
                smsSuccess = smsSuccess,
                emailSuccess = emailSuccess,
                errors = errors
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error sending birthday wish", e)
            Result(
                smsSuccess = false,
                emailSuccess = false,
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    /**
     * ✅ Send SMS with callback support (like TaskAutomationManager)
     * Waits for SMS result using suspend coroutine
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sendSMSWithCallback(
        phoneNumber: String,
        message: String
    ): Boolean = suspendCancellableCoroutine { continuation ->

        // Set callback
        onSMSStatusCallback = { success, statusMessage ->
            Log.d(TAG, "SMS Callback: success=$success, msg=$statusMessage")
            continuation.resume(success)
        }

        // Send SMS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sendSMS(phoneNumber, message)
        } else {
            sendSMSLegacy(phoneNumber, message)
        }

        // Cleanup on cancellation
        continuation.invokeOnCancellation {
            onSMSStatusCallback = null
        }
    }

    /**
     * ✅ Send SMS (Android O+) - Copied from TaskAutomationManager
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            if (!hasPermission(Manifest.permission.SEND_SMS)) {
                Log.e(TAG, "SMS permission not granted")
                onSMSStatusCallback?.invoke(false, "Permission denied")
                return
            }

            // Clean phone number
            val cleanNumber = phoneNumber.replace(Regex("[\\s-]"), "")

            Log.d(TAG, "📱 Sending SMS to: $cleanNumber")

            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            registerSMSReceivers()

            // ✅ Get SmsManager based on Android version
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // ✅ Handle long messages
            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                val sentPIs = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentPIs.add(sentPI)
                }
                smsManager.sendMultipartTextMessage(
                    cleanNumber,
                    null,
                    parts,
                    sentPIs,
                    null
                )
                Log.d(TAG, "Sending multi-part SMS (${parts.size} parts) to $cleanNumber")
            } else {
                smsManager.sendTextMessage(
                    cleanNumber,
                    null,
                    message,
                    sentPI,
                    null
                )
                Log.d(TAG, "Sending single SMS to $cleanNumber")
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
            onSMSStatusCallback?.invoke(false, "Failed to send: ${e.message}")
        }
    }

    /**
     * ✅ Send SMS (Legacy - Below Android O)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Suppress("DEPRECATION")
    private fun sendSMSLegacy(phoneNumber: String, message: String) {
        try {
            if (!hasPermission(Manifest.permission.SEND_SMS)) {
                Log.e(TAG, "SMS permission not granted")
                onSMSStatusCallback?.invoke(false, "Permission denied")
                return
            }

            val cleanNumber = phoneNumber.replace(Regex("[\\s-]"), "")
            Log.d(TAG, "📱 Sending SMS (legacy) to: $cleanNumber")

            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            registerSMSReceivers()

            val smsManager = SmsManager.getDefault()

            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                val sentPIs = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentPIs.add(sentPI)
                }
                smsManager.sendMultipartTextMessage(
                    cleanNumber,
                    null,
                    parts,
                    sentPIs,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    cleanNumber,
                    null,
                    message,
                    sentPI,
                    null
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
            onSMSStatusCallback?.invoke(false, "Failed to send: ${e.message}")
        }
    }

    /**
     * ✅ Register SMS receivers (like TaskAutomationManager)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerSMSReceivers() {
        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "✅ SMS sent successfully")
                        onSMSStatusCallback?.invoke(true, "Message sent successfully")
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "❌ SMS generic failure")
                        onSMSStatusCallback?.invoke(false, "Failed to send message")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "❌ SMS no service")
                        onSMSStatusCallback?.invoke(false, "No network service")
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "❌ SMS null PDU")
                        onSMSStatusCallback?.invoke(false, "Message format error")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "❌ SMS radio off")
                        onSMSStatusCallback?.invoke(false, "Phone radio is off")
                    }
                }
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver", e)
                }
            }
        }

        try {
            context.registerReceiver(
                sentReceiver,
                IntentFilter("SMS_SENT"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver", e)
        }
    }

    /**
     * ✅ Check permission
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * ✅ Send Email using EmailManager
     */
    private suspend fun sendEmail(
        recipientEmail: String,
        recipientName: String,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val subject = "🎉 Happy Birthday $recipientName!"

            val success = emailManager.sendEmail(
                to = recipientEmail,
                subject = subject,
                body = message
            )

            if (success) {
                Log.d(TAG, "✅ Email sent to $recipientEmail")
            } else {
                Log.e(TAG, "❌ Email failed for $recipientEmail")
            }

            success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Email error: ${e.message}", e)
            false
        }
    }

    /**
     * Generate birthday message
     */
    fun generateBirthdayMessage(contact: Contact): String {
        val age = contact.getAge()

        return if (contact.customMessage != null) {
            contact.customMessage!!
        } else if (age != null && age > 0 && age < 120) {
            """
            🎉 Happy ${age}th Birthday ${contact.name}! 🎂
            
            Wishing you a wonderful day filled with joy and happiness! 🎈
            
            May this year bring you success, good health, and lots of smiles! 😊
            
            Best wishes,
            Sent via Groot 🤖
            """.trimIndent()
        } else {
            """
            🎉 Happy Birthday ${contact.name}! 🎂
            
            Wishing you a fantastic day ahead! 🎈
            
            May all your dreams come true this year! ✨
            
            Best wishes,
            Sent via Groot 🤖
            """.trimIndent()
        }
    }

    /**
     * Generate Hindi birthday message
     */
    fun generateHindiBirthdayMessage(contact: Contact): String {
        val age = contact.getAge()

        return if (age != null && age > 0 && age < 120) {
            """
            🎉 ${contact.name} को ${age}वें जन्मदिन की हार्दिक शुभकामनाएं! 🎂
            
            आपका दिन खुशियों से भरा हो! 🎈
            
            आपको अच्छी सेहत और सफलता मिले! 😊
            
            शुभकामनाएं,
            Groot 🤖 द्वारा भेजा गया
            """.trimIndent()
        } else {
            """
            🎉 ${contact.name} को जन्मदिन की हार्दिक शुभकामनाएं! 🎂
            
            आपका दिन मंगलमय हो! 🎈
            
            आपकी सभी मनोकामनाएं पूर्ण हों! ✨
            
            शुभकामनाएं,
            Groot 🤖 द्वारा भेजा गया
            """.trimIndent()
        }
    }

    /**
     * Generate custom message from template
     */
    fun generateCustomMessage(
        contact: Contact,
        template: String
    ): String {
        val age = contact.getAge()

        return template
            .replace("{name}", contact.name)
            .replace("{age}", age?.toString() ?: "")
            .replace("{phone}", contact.phoneNumber)
    }

    /**
     * Result class
     */
    data class Result(
        val smsSuccess: Boolean,
        val emailSuccess: Boolean,
        val errors: List<String> = emptyList()
    ) {
        val anySuccess: Boolean
            get() = smsSuccess || emailSuccess

        val bothSuccess: Boolean
            get() = smsSuccess && emailSuccess
    }
}