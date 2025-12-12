package com.example.groot.email

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class EmailManager(private val context: Context) {

    companion object {
        private const val TAG = "EmailManager"
    }

    // Email credentials
    private var senderEmail: String? = null
    private var senderPassword: String? = null

    // Contact storage
    private val contacts = mutableMapOf<String, String>()

    /**
     * Setup email credentials (call once in initialization)
     */
    fun setupEmailCredentials(email: String, password: String) {
        this.senderEmail = email
        this.senderPassword = password
        Log.d(TAG, "✅ Email credentials configured for: $email")
    }

    /**
     * Add contact with email
     */
    fun addContact(name: String, email: String) {
        contacts[name.lowercase()] = email
        Log.d(TAG, "📇 Contact added: $name -> $email")
    }

    /**
     * Get email for contact name
     */
    fun getContactEmail(name: String): String? {
        return contacts[name.lowercase()]
    }

    /**
     * Parse email command from voice input - SIMPLIFIED
     */
    fun parseEmailCommand(command: String, isHindi: Boolean): ParsedEmailCommand? {
        try {
            Log.d(TAG, "📧 parseEmailCommand: $command")

            // Clean command
            val cleanedCommand = command
                .replaceFirst("(?i)^groot\\s+".toRegex(), "")
                .trim()

            // Try to extract recipient
            val recipientName = extractRecipientName(cleanedCommand, isHindi)

            if (recipientName == null) {
                Log.w(TAG, "❌ Could not extract recipient from: $cleanedCommand")
                return null
            }

            Log.d(TAG, "✅ Extracted recipient: $recipientName")

            // Get email for recipient
            val recipientEmail = getContactEmail(recipientName)

            if (recipientEmail == null) {
                Log.w(TAG, "❌ No email found for: $recipientName")
                return null
            }

            Log.d(TAG, "✅ Found email: $recipientEmail")

            // For now, always return empty subject/body
            // Content will be collected interactively
            return ParsedEmailCommand(
                recipientName = recipientName,
                recipientEmail = recipientEmail,
                subject = null,
                body = "",
                scheduleTime = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse error: ${e.message}", e)
            return null
        }
    }

    /**
     * Extract recipient name from command
     */
    private fun extractRecipientName(command: String, isHindi: Boolean): String? {
        // Pattern 1: "ram ko mail kro" or "send email to ram"
        val pattern1 = if (isHindi) {
            Regex("(\\w+)\\s+ko\\s+(?:mail|email|e-mail)", RegexOption.IGNORE_CASE)
        } else {
            Regex("(?:mail|email|send)\\s+(?:to|for)?\\s+(\\w+)", RegexOption.IGNORE_CASE)
        }

        pattern1.find(command)?.let {
            return it.groupValues[1].trim()
        }

        // Pattern 2: "ram ko email bhejo"
        val pattern2 = Regex("(\\w+)\\s+ko\\s+(?:email|mail)\\s+(?:bhejo|kro|send)", RegexOption.IGNORE_CASE)
        pattern2.find(command)?.let {
            return it.groupValues[1].trim()
        }

        return null
    }

    /**
     * Extract schedule time if mentioned
     */
    private fun extractScheduleTime(command: String, isHindi: Boolean): Long? {
        // Check for time mentions like "kal", "tomorrow", "5 bje", "at 5 pm"

        // For now, return null (immediate send)
        // You can integrate with ReminderManager's time parsing later
        return null
    }

    /**
     * Extract subject if explicitly mentioned
     */
    private fun extractSubject(command: String, isHindi: Boolean): String? {
        // Pattern: "subject: xyz" or "विषय: xyz"
        val pattern = Regex("(?:subject|विषय)\\s*[:：]?\\s*(.+?)(?:,|\\.|$)", RegexOption.IGNORE_CASE)
        pattern.find(command)?.let {
            return it.groupValues[1].trim()
        }

        return null
    }

    /**
     * Extract body if explicitly mentioned
     */
    private fun extractBody(command: String, isHindi: Boolean): String {
        // Pattern: "ki xyz" or "regarding xyz" or "about xyz"
        val patterns = listOf(
            Regex("\\s+ki\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("\\s+regarding\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("\\s+about\\s+(.+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(command)?.let {
                return it.groupValues[1].trim()
            }
        }

        return ""
    }

    /**
     * Send email via JavaMail API
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        maxRetries: Int = 3
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "senderEmail $senderEmail");
        Log.d(TAG, "senderPassword $senderPassword");
        if (senderEmail == null || senderPassword == null) {
            Log.e(TAG, "❌ Email credentials not configured")
            return@withContext false
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                attempt++
                Log.d(TAG, "📧 Sending email (attempt $attempt/$maxRetries)...")

                // Gmail SMTP configuration
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "587")
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                }

                // Create session with authenticator
                val session = javax.mail.Session.getInstance(props,
                    object : javax.mail.Authenticator() {
                        override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                            return javax.mail.PasswordAuthentication(senderEmail, senderPassword)
                        }
                    })

                // Create message
                val message = javax.mail.internet.MimeMessage(session).apply {
                    setFrom(javax.mail.internet.InternetAddress(senderEmail))
                    setRecipients(
                        javax.mail.Message.RecipientType.TO,
                        javax.mail.internet.InternetAddress.parse(to)
                    )
                    this.subject = subject
                    setText(body, "UTF-8")
                }

                // Send
                javax.mail.Transport.send(message)

                Log.d(TAG, "✅ Email sent successfully to $to")
                return@withContext true

            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "❌ Email send failed (attempt $attempt): ${e.message}")

                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(2000) // Wait before retry
                }
            }
        }

        Log.e(TAG, "❌ Email failed after $maxRetries attempts: ${lastException?.message}")
        return@withContext false
    }

    /**
     * Data class for parsed email command
     */
    data class ParsedEmailCommand(
        val recipientName: String,
        val recipientEmail: String,
        val subject: String?,
        val body: String,
        val scheduleTime: Long? = null
    )
}