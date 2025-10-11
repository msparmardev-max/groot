package com.example.groot

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.app.Activity

class TaskAutomationManager(private val context: Context) {

    companion object {
        private const val TAG = "TaskAutomationManager"

        // App packages
        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "camera" to "com.android.camera2",
            "phone" to "com.android.dialer",
            "dialer" to "com.android.dialer",
            "gallery" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "maps" to "com.google.android.apps.maps",
            "google" to "com.google.android.googlequicksearchbox"
        )
    }
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var callStateListener: CallStateListener? = null
    private var onCallEndCallback: (() -> Unit)? = null
    private var onSMSStatusCallback: ((Boolean, String) -> Unit)? = null

    suspend fun executeAction(action: String, target: String, confidence: Double) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Executing: $action -> '$target'")

        when (action.lowercase().trim()) {
            "call" -> makeCall(target)
            "sms" -> {
                val parts = target.split(":", limit = 2)
                if (parts.size == 2) {
                    sendSMS(parts[0], parts[1])
                }
            }
            "search" -> searchWeb(target)
            "open_app" -> openApp(target)
            "mobile_data" -> openMobileDataSettings()
            "wifi" -> openWifiSettings()
            "settings" -> openSettings()
            "add_contact" -> handleAddContact(target)
            "get_last_call" -> getLastCallInfo()
            else -> {
                if (target.isNotBlank()) {
                    makeCall(target)
                }
            }
        }
    }

    /**
     * Make a phone call
     */
    private fun makeCall(target: String) {
        try {
            if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                Log.e(TAG, "Call permission not granted")
                return
            }

            val phoneNumber = resolveContactFromPhone(target)

            // Clean the number - remove spaces, dashes, parentheses
            val cleanedNumber = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")

            // Now validate
            if (!cleanedNumber.matches(Regex("^[+]?[0-9]{10,13}$"))) {
                Log.e(TAG, "Invalid number: $phoneNumber (cleaned: $cleanedNumber) for: $target")
                return
            }

            Log.i(TAG, "Calling: $cleanedNumber (original: $phoneNumber)")

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanedNumber")  // Use cleaned number
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call failed", e)
        }
    }

    /**
     * Send SMS
     */
    fun setSMSStatusCallback(callback: (success: Boolean, message: String) -> Unit) {
        onSMSStatusCallback = callback
    }

    private fun sendSMS(target: String, message: String) {
        try {
            if (!hasPermission(Manifest.permission.SEND_SMS)) {
                Log.e(TAG, "SMS permission not granted")
                onSMSStatusCallback?.invoke(false, "Permission denied")
                return
            }

            val phoneNumber = resolveContactFromPhone(target)

            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            registerSMSReceivers()

            SmsManager.getDefault().sendTextMessage(
                phoneNumber,
                null,
                message,
                sentPI,
                null
            )
            Log.d(TAG, "SMS sending to $phoneNumber")

        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
            onSMSStatusCallback?.invoke(false, "Failed to send: ${e.message}")
        }
    }

    private fun registerSMSReceivers() {
        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.i(TAG, "SMS sent successfully")
                        onSMSStatusCallback?.invoke(true, "Message sent successfully")
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "SMS generic failure")
                        onSMSStatusCallback?.invoke(false, "Failed to send message")
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "SMS no service")
                        onSMSStatusCallback?.invoke(false, "No network service")
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "SMS null PDU")
                        onSMSStatusCallback?.invoke(false, "Message format error")
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "SMS radio off")
                        onSMSStatusCallback?.invoke(false, "Phone radio is off")
                    }
                }
                context?.unregisterReceiver(this)
            }
        }

        context.registerReceiver(
            sentReceiver,
            IntentFilter("SMS_SENT"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    fun setCallEndCallback(callback: () -> Unit) {
        onCallEndCallback = callback
    }

    fun startCallMonitoring() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            Log.e(TAG, "READ_PHONE_STATE permission not granted")
            return
        }

        callStateListener = CallStateListener {
            onCallEndCallback?.invoke()
            stopCallMonitoring()
        }

        telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d(TAG, "Call monitoring started")
    }

    fun stopCallMonitoring() {
        callStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            callStateListener = null
            Log.d(TAG, "Call monitoring stopped")
        }
    }

    /**
     * Resolve contact name to phone number from device contacts
     */
    private fun resolveContactFromPhone(name: String): String {
        val cleanName = name.trim().lowercase()
            .removePrefix("call ")
            .removePrefix("phone ")
            .removePrefix("dial ")
            .removePrefix("कॉल ")
            .trim()

        if (cleanName.matches(Regex("^[+]?[0-9]{10,13}$"))) {
            return cleanName
        }

        val correctedName = cleanName
            .replace("baina", "bahina")
            .replace("behna", "bahina")
            .replace("bina", "beena")
            .replace("binay", "vinay")
            .replace("biney", "vinay")
            .replace("bhaiya ji", "bhaiyaji")
            .replace("bhayajii", "bhaiyaji")
            .replace("byaji", "bhaiyaji")

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            val phoneNumber = searchContactByName(correctedName)
            if (phoneNumber != null) {
                Log.d(TAG, "Found: $correctedName -> $phoneNumber")
                return phoneNumber
            }
        }

        Log.w(TAG, "Contact not resolved: $cleanName")
        return name
    }

    /**
     * Search contact by name in phone's contact list
     */
    fun searchContactByName(name: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "READ_CONTACTS permission not granted")
            return null
        }

        val cleanName = name.trim().lowercase()
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        var exactMatch: String? = null
        val partialMatches = mutableListOf<Pair<String, String>>()

        try {
            cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val contactName = it.getString(nameIndex)?.lowercase() ?: continue
                    val phoneNumber = it.getString(numberIndex) ?: continue

                    if (contactName == cleanName) {
                        exactMatch = phoneNumber
                    }

                    if (contactName.contains(cleanName) || cleanName.contains(contactName)) {
                        partialMatches.add(Pair(contactName, phoneNumber))
                    }
                }
            }

            if (exactMatch != null) {
                Log.d(TAG, "Exact match found for: $cleanName")
                return exactMatch
            }

            if (partialMatches.size == 1) {
                Log.d(TAG, "Single partial match: ${partialMatches[0].first}")
                return partialMatches[0].second
            }

            if (partialMatches.size > 1) {
                Log.w(TAG, "Multiple matches for '$cleanName': ${partialMatches.map { it.first }}")
                val bestMatch = partialMatches.minByOrNull { it.first.length }
                Log.i(TAG, "Using shortest match: ${bestMatch?.first}")
                return bestMatch?.second
            }

            Log.w(TAG, "No contact found for: $cleanName")

        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * Search contacts with disambiguation support
     * Returns single match or multiple matches for user selection
     */
    fun searchContactWithDisambiguation(name: String): ContactSearchResult {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "READ_CONTACTS permission not granted")
            return ContactSearchResult(isSingleMatch = false, multipleMatches = emptyList())
        }

        val cleanName = name.trim().lowercase()
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        var exactMatch: ConversationStateManager.ContactOption? = null
        val partialMatches = mutableListOf<ConversationStateManager.ContactOption>()

        try {
            cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val contactName = it.getString(nameIndex) ?: continue
                    val phoneNumber = it.getString(numberIndex) ?: continue
                    val contactNameLower = contactName.lowercase()

                    // Exact match
                    if (contactNameLower == cleanName) {
                        exactMatch = ConversationStateManager.ContactOption(contactName, phoneNumber)
                    }

                    // Partial match
                    if (contactNameLower.contains(cleanName) || cleanName.contains(contactNameLower)) {
                        partialMatches.add(ConversationStateManager.ContactOption(contactName, phoneNumber))
                    }
                }
            }

            // Single exact match
            if (exactMatch != null && partialMatches.size == 1) {
                Log.d(TAG, "Single exact match: ${exactMatch.displayName}")
                return ContactSearchResult(
                    isSingleMatch = true,
                    singleContact = exactMatch.number
                )
            }

            // Single partial match
            if (partialMatches.size == 1) {
                Log.d(TAG, "Single partial match: ${partialMatches[0].displayName}")
                return ContactSearchResult(
                    isSingleMatch = true,
                    singleContact = partialMatches[0].number
                )
            }

            /// Multiple matches - need disambiguation
            if (partialMatches.size > 1) {
                Log.w(TAG, "Multiple matches (${partialMatches.size}) for: $cleanName")

                // Smart filtering and sorting
                val filteredMatches = filterAndSortContacts(partialMatches)

                Log.i(TAG, "After filtering: ${filteredMatches.size} contacts")
                return ContactSearchResult(
                    isSingleMatch = false,
                    multipleMatches = filteredMatches
                )
            }

            // No match
            Log.w(TAG, "No contact found for: $cleanName")
            return ContactSearchResult(isSingleMatch = false, multipleMatches = emptyList())

        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts with disambiguation", e)
            return ContactSearchResult(isSingleMatch = false, multipleMatches = emptyList())
        } finally {
            cursor?.close()
        }
    }

    /**
     * Get recent call numbers for prioritization
     */
    private fun getRecentCallNumbers(limit: Int = 20): Set<String> {
        val recentNumbers = mutableSetOf<String>()

        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return recentNumbers
        }

        val contentResolver: ContentResolver = context.contentResolver
        val projection = arrayOf(CallLog.Calls.NUMBER)

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)?.replace(Regex("[\\s\\-\\(\\)]"), "")
                    if (number != null) {
                        recentNumbers.add(number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent calls", e)
        } finally {
            cursor?.close()
        }

        return recentNumbers
    }

    /**
     * Smart filter and sort contacts by recency and duplicates
     */
    fun filterAndSortContacts(contacts: List<ConversationStateManager.ContactOption>): List<ConversationStateManager.ContactOption> {
        // Get recent call numbers for prioritization
        val recentNumbers = getRecentCallNumbers()

        // Remove exact duplicates by number
        val uniqueByNumber = contacts.distinctBy {
            it.number.replace(Regex("[\\s\\-\\(\\)]"), "")
        }

        // Sort by: 1) Recent calls first, 2) Shorter names (likely more specific)
        val sorted = uniqueByNumber.sortedWith(
            compareByDescending<ConversationStateManager.ContactOption> { contact ->
                val cleanNumber = contact.number.replace(Regex("[\\s\\-\\(\\)]"), "")
                if (recentNumbers.contains(cleanNumber)) 1 else 0
            }.thenBy { it.displayName.length }
        )

        Log.d(TAG, "Filtered ${contacts.size} contacts to ${sorted.size} unique contacts")
        return sorted
    }

    /**
     * Data class for search results
     */
    data class ContactSearchResult(
        val isSingleMatch: Boolean,
        val singleContact: String? = null,
        val multipleMatches: List<ConversationStateManager.ContactOption>? = null
    )

    /**
     * Get all contacts from phone
     */
    fun getAllPhoneContacts(): Map<String, String> {
        val contacts = mutableMapOf<String, String>()

        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "READ_CONTACTS permission not granted")
            return contacts
        }

        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: continue
                    val number = it.getString(numberIndex) ?: continue
                    contacts[name.lowercase()] = number
                }
            }

            Log.d(TAG, "Loaded ${contacts.size} contacts from phone")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contacts", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * Get last call information (incoming/outgoing/missed)
     */
    fun getLastCallInfo(): String? {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            Log.e(TAG, "READ_CALL_LOG permission not granted")
            return null
        }

        val contentResolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

                    val phoneNumber = it.getString(numberIndex)
                    val callType = it.getInt(typeIndex)

                    val typeStr = when (callType) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "unknown"
                    }

                    Log.d(TAG, "Last call: $phoneNumber ($typeStr)")
                    return phoneNumber
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * Get recent call logs
     */
    fun getRecentCalls(limit: Int = 10): List<CallInfo> {
        val calls = mutableListOf<CallInfo>()

        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            Log.e(TAG, "READ_CALL_LOG permission not granted")
            return calls
        }

        val contentResolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT $limit"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    val type = it.getInt(typeIndex)
                    val date = it.getLong(dateIndex)
                    val duration = it.getInt(durationIndex)
                    val name = it.getString(nameIndex)

                    calls.add(CallInfo(number, type, date, duration, name))
                }
            }

            Log.d(TAG, "Retrieved ${calls.size} recent calls")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log", e)
        } finally {
            cursor?.close()
        }

        return calls
    }

    /**
     * Save contact to phone
     */
    fun saveContactToPhone(name: String, phoneNumber: String): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            Log.e(TAG, "WRITE_CONTACTS permission not granted")
            return false
        }

        try {
            val contentResolver: ContentResolver = context.contentResolver
            val operations = ArrayList<android.content.ContentProviderOperation>()

            // Create contact
            operations.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Add name
            operations.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Add phone number
            operations.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            Log.i(TAG, "Contact saved: $name -> $phoneNumber")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact", e)
            return false
        }
    }

    /**
     * Handle add contact command
     */
    private fun handleAddContact(target: String) {
        try {
            Log.i(TAG, "target : $target")
            var parts = target.split(":", limit = 2)
            Log.i(TAG, "parts : $parts")
            Log.i(TAG, "parts.size : ${parts.size}")

            // ✅ Case 1: Last call
            if (target.lowercase().contains("last call") || target.contains("पिछली कॉल") || target.contains("अंतिम कॉल")) {
                val lastNumber = getLastCallInfo()
                if (lastNumber != null) {
                    // Flexible regex for English/Hindi
                    val nameMatch = Regex("as\\s+([\\p{L} ]+)", RegexOption.IGNORE_CASE).find(target)
                        ?: Regex("को\\s+([\\p{L} ]+)", RegexOption.IGNORE_CASE).find(target)

                    val contactName = nameMatch?.groupValues?.get(1)?.trim()
                        ?: target.trim().split(" ").lastOrNull() ?: ""

                    if (contactName.isNotEmpty()) {
                        saveContactToPhone(contactName, lastNumber)
                        Log.i(TAG, "Saved last call as: $contactName")
                        return
                    }
                }
            }

            // ✅ Case 2: Explicit "name:number" format
            if (parts.size == 2) {
                val name = parts[0].trim()
                var number = parts[1].trim().replace(Regex("[^0-9+]"), "")

                if (!number.startsWith("+") && number.length == 10) {
                    number = "+91$number"
                }

                if (number.matches(Regex("^\\+?[0-9]{10,13}$"))) {
                    saveContactToPhone(name, number)
                    Log.e(TAG, "Add contact success : $name $number")
                    return
                }
            }

            // ✅ Case 3: Fallback (use helper functions to extract name & number)
            val name = ContactUtils.extractContactName(target)
            val number = ContactUtils.extractPhoneNumber(target)

            if (name.isNotEmpty() && number.isNotEmpty()) {
                saveContactToPhone(name, number)
                Log.e(TAG, "Fallback add contact success : $name $number")
            } else {
                Log.e(TAG, "Add contact failed: Could not extract name/number from '$target'")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Add contact failed", e)
        }
    }


    /**
     * Open app
     */
    private fun openApp(appName: String) {
        try {
            val packageName = resolveAppPackage(appName)
            Log.i(TAG, "Opening: $packageName")

            val intent = context.packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Opened: $packageName")
            } else {
                Log.w(TAG, "App not installed: $packageName")

                if (appName.lowercase().contains("chrome") || appName.lowercase().contains("browser")) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(browserIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open: $appName", e)
        }
    }

    private fun searchWeb(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
        }
    }

    private fun openMobileDataSettings() {
        try {
            val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open data settings", e)
        }
    }

    private fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi settings", e)
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Settings failed", e)
        }
    }

    private fun resolveAppPackage(appName: String): String {
        val cleanName = appName.trim().lowercase()
        APP_PACKAGES[cleanName]?.let { return it }

        APP_PACKAGES.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }?.let { return it.value }

        return appName
    }

    /**
     * Check if permission is granted
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Data class for call information
     */
    data class CallInfo(
        val number: String,
        val type: Int,
        val date: Long,
        val duration: Int,
        val name: String?
    )
    class CallStateListener(
        private val onCallEnded: () -> Unit
    ) : PhoneStateListener() {

        private var wasRinging = false
        private var wasOffHook = false

        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    wasRinging = true
                    Log.d("CallStateListener", "Call ringing")
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    wasOffHook = true
                    Log.d("CallStateListener", "Call active")
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    if (wasOffHook || wasRinging) {
                        Log.d("CallStateListener", "Call ended")
                        wasRinging = false
                        wasOffHook = false
                        onCallEnded()
                    }
                }
            }
        }
    }
}