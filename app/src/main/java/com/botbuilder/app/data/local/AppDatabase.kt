package com.botbuilder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)
}

@Database(
    entities = [ReplyRule::class, ConversationLog::class, KnownContact::class, FileDeliveryRule::class, BotCommand::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun replyRuleDao(): ReplyRuleDao
    abstract fun conversationLogDao(): ConversationLogDao
    abstract fun knownContactDao(): KnownContactDao
    abstract fun fileDeliveryRuleDao(): FileDeliveryRuleDao
    abstract fun botCommandDao(): BotCommandDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "botbuilder.db"
                )
                    // App is still in active development/testing — safe to reset local data
                    // on schema changes instead of hand-writing migrations for every version bump.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
