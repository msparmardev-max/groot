package com.example.groot.birthday

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for Birthday System
 */
@Database(
    entities = [Contact::class],
    version = 1,
    exportSchema = false
)
abstract class BirthdayDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: BirthdayDatabase? = null

        /**
         * Get database instance (Singleton pattern)
         */
        fun getDatabase(context: Context): BirthdayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BirthdayDatabase::class.java,
                    "birthday_database"
                )
                    .fallbackToDestructiveMigration() // ⚠️ Use migrations in production
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Reset database (for testing/debugging)
         */
        fun resetDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                context.deleteDatabase("birthday_database")
                INSTANCE = null
            }
        }

        // ==================== MIGRATIONS (Future Use) ====================

        /**
         * Example migration from version 1 to 2
         * Add this when you update database schema
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example: Add new column
                // database.execSQL("ALTER TABLE contacts ADD COLUMN newColumn TEXT")
            }
        }
    }
}