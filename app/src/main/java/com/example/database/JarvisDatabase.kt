package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.history.ConversationDao
import com.example.history.ConversationEntity
import com.example.memory.ConversationSummaryDao
import com.example.memory.ConversationSummaryEntity
import com.example.memory.MemoryDao
import com.example.memory.MemoryEntity
import com.example.memory.MemoryEventDao
import com.example.memory.MemoryEventEntity

@Database(
    entities = [
        MemoryEntity::class,
        MemoryEventEntity::class,
        ConversationSummaryEntity::class,
        ConversationEntity::class,
        AutomationHistoryEntity::class,
        UpdateHistoryEntity::class,
        UserCommandPreferenceEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryEventDao(): MemoryEventDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun automationHistoryDao(): AutomationHistoryDao
    abstract fun updateHistoryDao(): UpdateHistoryDao
    abstract fun userCommandPreferenceDao(): UserCommandPreferenceDao

    fun commandPreferenceDao(): UserCommandPreferenceDao = userCommandPreferenceDao()

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `command` TEXT NOT NULL,
                        `tool` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `result` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `update_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `versionCode` INTEGER NOT NULL,
                        `versionName` TEXT NOT NULL,
                        `installedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `releaseNotes` TEXT NOT NULL,
                        `channel` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_command_preferences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `commandKey` TEXT NOT NULL,
                        `preferredTool` TEXT NOT NULL,
                        `requiresConfirmation` INTEGER NOT NULL,
                        `autoExecute` INTEGER NOT NULL,
                        `voiceFeedbackEnabled` INTEGER NOT NULL,
                        `customFeedback` TEXT,
                        `parametersJson` TEXT,
                        `isEnabled` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `memoryId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `content` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `memoryType` TEXT NOT NULL DEFAULT 'LONG_TERM_MEMORY'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `importance` REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER_EXPLICIT'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `expirationPolicy` TEXT NOT NULL DEFAULT 'PERMANENT'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `userApproved` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE `memories` SET `content` = `value` WHERE `content` = ''")
                db.execSQL("UPDATE `memories` SET `memoryId` = CAST(`id` AS TEXT) WHERE `memoryId` = ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_summaries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `timeRange` TEXT NOT NULL,
                        `messageCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

typealias AppDatabase = JarvisDatabase
