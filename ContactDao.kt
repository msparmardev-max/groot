package com.example.groot.birthday

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Contact operations
 */
@Dao
interface ContactDao {

    // ==================== INSERT ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>)

    // ==================== UPDATE ====================

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("UPDATE contacts SET lastWishedYear = :year WHERE contactId = :contactId")
    suspend fun updateLastWishedYear(contactId: String, year: Int)

    @Query("UPDATE contacts SET dateOfBirth = :dob WHERE contactId = :contactId")
    suspend fun updateDateOfBirth(contactId: String, dob: String)

    @Query("UPDATE contacts SET isAutoWishEnabled = :enabled WHERE contactId = :contactId")
    suspend fun updateAutoWish(contactId: String, enabled: Boolean)

    @Query("UPDATE contacts SET customMessage = :message WHERE contactId = :contactId")
    suspend fun updateCustomMessage(contactId: String, message: String?)

    @Query("UPDATE contacts SET email = :email WHERE contactId = :contactId")
    suspend fun updateEmail(contactId: String, email: String?)

    @Query("UPDATE contacts SET wishViaEmail = :enabled WHERE contactId = :contactId")
    suspend fun updateWishViaEmail(contactId: String, enabled: Boolean)

    @Query("UPDATE contacts SET wishViaSMS = :enabled WHERE contactId = :contactId")
    suspend fun updateWishViaSMS(contactId: String, enabled: Boolean)

    // ==================== DELETE ====================

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE contactId = :contactId")
    suspend fun deleteContactById(contactId: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    // ==================== QUERY ====================

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContacts(): List<Contact>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContactsFlow(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: String): Contact?

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :name || '%'")
    suspend fun searchContactsByName(name: String): List<Contact>

    @Query("SELECT * FROM contacts WHERE dateOfBirth IS NOT NULL ORDER BY name ASC")
    suspend fun getContactsWithBirthday(): List<Contact>

    @Query("SELECT * FROM contacts WHERE dateOfBirth IS NOT NULL AND isAutoWishEnabled = 1")
    suspend fun getContactsWithAutoWishEnabled(): List<Contact>

    /**
     * Get contacts whose birthday is today
     * Note: Room doesn't support complex date matching, so we'll filter in code
     */
    @Query("SELECT * FROM contacts WHERE dateOfBirth IS NOT NULL AND isAutoWishEnabled = 1")
    suspend fun getPotentialBirthdaysToday(): List<Contact>

    /**
     * Get upcoming birthdays (next 30 days)
     * We'll filter this in code as well
     */
    @Query("SELECT * FROM contacts WHERE dateOfBirth IS NOT NULL ORDER BY dateOfBirth ASC")
    suspend fun getUpcomingBirthdays(): List<Contact>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE dateOfBirth IS NOT NULL")
    suspend fun getContactsWithBirthdayCount(): Int

    @Query("SELECT * FROM contacts WHERE email IS NOT NULL AND email != ''")
    suspend fun getContactsWithEmail(): List<Contact>

    @Query("SELECT COUNT(*) FROM contacts WHERE email IS NOT NULL AND email != ''")
    suspend fun getContactsWithEmailCount(): Int
}