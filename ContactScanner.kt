package com.example.groot.birthday

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scans phone contacts and extracts birthday information
 */
class ContactScanner(private val context: Context) {

    companion object {
        private const val TAG = "ContactScanner"
    }

    /**
     * Scan all phone contacts and extract those with birthdays
     */
    suspend fun scanContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<Contact>()

        try {
            Log.d(TAG, "🔍 Starting contact scan...")

            // Step 1: Get all contacts with phone numbers
            val contactsMap = getContactsWithPhoneNumbers()
            Log.d(TAG, "📱 Found ${contactsMap.size} contacts with phone numbers")

            // Step 2: Get birthdays for these contacts
            val birthdaysMap = getBirthdaysForContacts(contactsMap.keys)
            Log.d(TAG, "🎂 Found ${birthdaysMap.size} contacts with birthdays")

            // Step 3: Merge data
            contactsMap.forEach { (contactId, contactData) ->
                val birthday = birthdaysMap[contactId]

                contactsList.add(
                    Contact(
                        contactId = contactId,
                        name = contactData.first,
                        phoneNumber = contactData.second,
                        dateOfBirth = birthday,
                        lastWishedYear = null,
                        isAutoWishEnabled = birthday != null // Auto-enable if birthday exists
                    )
                )
            }

            Log.d(TAG, "✅ Scan complete: ${contactsList.size} total contacts")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning contacts", e)
        }

        contactsList
    }

    /**
     * Get all contacts with phone numbers
     * Returns Map<ContactId, Pair<Name, PhoneNumber>>
     */
    private fun getContactsWithPhoneNumbers(): Map<String, Pair<String, String>> {
        val contactsMap = mutableMapOf<String, Pair<String, String>>()

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val contactId = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: "Unknown"
                val phoneNumber = it.getString(numberIndex) ?: continue

                // Store first phone number for each contact
                if (!contactsMap.containsKey(contactId)) {
                    contactsMap[contactId] = Pair(name, phoneNumber.replace("\\s".toRegex(), ""))
                }
            }
        }

        return contactsMap
    }

    /**
     * Get birthdays for specific contacts
     * Returns Map<ContactId, Birthday (dd/MM/yyyy)>
     */
    private fun getBirthdaysForContacts(contactIds: Set<String>): Map<String, String> {
        val birthdaysMap = mutableMapOf<String, String>()

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE
            ),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            ),
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val dateIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)

            while (it.moveToNext()) {
                val contactId = it.getString(idIndex)
                val dateString = it.getString(dateIndex)

                if (contactId != null && dateString != null) {
                    val formattedDate = formatBirthday(dateString)
                    if (formattedDate != null) {
                        birthdaysMap[contactId] = formattedDate
                        Log.d(TAG, "🎂 Birthday found: Contact $contactId -> $formattedDate")
                    }
                }
            }
        }

        return birthdaysMap
    }

    /**
     * Format birthday from various formats to dd/MM/yyyy
     * Handles: "1990-03-15", "03/15/1990", "15-03-1990", "--03-15"
     */
    private fun formatBirthday(dateString: String): String? {
        try {
            // Remove leading dashes (e.g., "--03-15")
            val cleanedDate = dateString.trim().removePrefix("--")

            // Try multiple date formats
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("MM-dd", Locale.getDefault()), // Year-less
                SimpleDateFormat("MM/dd", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val date = format.parse(cleanedDate)
                    if (date != null) {
                        val calendar = Calendar.getInstance().apply { time = date }
                        val day = calendar.get(Calendar.DAY_OF_MONTH)
                        val month = calendar.get(Calendar.MONTH) + 1

                        // If year is not present, use 1900 as default
                        val year = if (cleanedDate.contains("-") && cleanedDate.split("-").size == 3) {
                            calendar.get(Calendar.YEAR)
                        } else {
                            1900
                        }

                        return String.format("%02d/%02d/%04d", day, month, year)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            Log.w(TAG, "⚠️ Could not parse birthday: $dateString")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error formatting birthday: $dateString", e)
        }

        return null
    }

    /**
     * Scan specific contact by name
     */
    suspend fun scanContactByName(name: String): Contact? = withContext(Dispatchers.IO) {
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val contactId = it.getString(idIndex)
                    val contactName = it.getString(nameIndex)
                    val phoneNumber = it.getString(numberIndex)

                    // Get birthday
                    val birthday = getBirthdaysForContacts(setOf(contactId))[contactId]

                    return@withContext Contact(
                        contactId = contactId,
                        name = contactName,
                        phoneNumber = phoneNumber,
                        dateOfBirth = birthday
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning contact by name", e)
        }

        null
    }
}