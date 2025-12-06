package com.example.groot.birthday

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Handles multi-channel communication (SMS, Email, WhatsApp)
 */
class CommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "CommunicationManager"
    }

    /**
     * Send birthday wish via multiple channels
     */
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
            // Send SMS
            if (viaSMS && contact.phoneNumber.isNotEmpty()) {
                smsSuccess = sendSMS(contact.phoneNumber, message)
                if (!smsSuccess) {
                    errors.add("SMS failed")
                }
            }

            // Send Email
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
     * Send SMS
     */
    private fun sendSMS(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                null,
                null
            )
            Log.d(TAG, "✅ SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS failed: ${e.message}", e)
            false
        }
    }

    /**
     * Send Email using Gmail SMTP
     */
    private suspend fun sendEmail(
        recipientEmail: String,
        recipientName: String,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get sender credentials from EmailPreferences
            val prefs = context.getSharedPreferences("email_prefs", Context.MODE_PRIVATE)
            val senderEmail = prefs.getString("sender_email", null)
            val senderPassword = prefs.getString("sender_password", null)

            if (senderEmail == null || senderPassword == null) {
                Log.e(TAG, "❌ Email credentials not configured")
                return@withContext false
            }

            // Setup email properties
            val properties = Properties().apply {
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
            }

            // Create session
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            })

            // Create message
            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail))
                addRecipient(Message.RecipientType.TO, InternetAddress(recipientEmail))
                subject = "🎉 Happy Birthday $recipientName!"

                // HTML email body
                setContent(
                    """
                    <html>
                    <body style="font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;">
                        <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">
                            <h1 style="color: #4CAF50; text-align: center;">🎉 Happy Birthday! 🎂</h1>
                            <p style="font-size: 16px; line-height: 1.6; color: #333;">
                                ${message.replace("\n", "<br>")}
                            </p>
                            <div style="text-align: center; margin-top: 30px;">
                                <p style="color: #999; font-size: 12px;">
                                    Sent with ❤️ via Groot - Your AI Assistant
                                </p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.trimIndent(),
                    "text/html"
                )
            }

            // Send email
            Transport.send(mimeMessage)

            Log.d(TAG, "✅ Email sent to $recipientEmail")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Email failed: ${e.message}", e)
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
            "🎉 Happy ${age}th Birthday ${contact.name}! 🎂\n\n" +
                    "Wishing you a wonderful day filled with joy and happiness! 🎈\n\n" +
                    "May this year bring you success, good health, and lots of smiles! 😊\n\n" +
                    "Best wishes,\n" +
                    "Sent via Groot 🤖"
        } else {
            "🎉 Happy Birthday ${contact.name}! 🎂\n\n" +
                    "Wishing you a fantastic day ahead! 🎈\n\n" +
                    "May all your dreams come true this year! ✨\n\n" +
                    "Best wishes,\n" +
                    "Sent via Groot 🤖"
        }
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