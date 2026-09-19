package com.example.chatfat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class ChatfatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var instance: ChatfatDatabase? = null

        fun getInstance(context: Context): ChatfatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatfatDatabase::class.java,
                    "chatfat.db"
                ).build().also { instance = it }
            }
    }
}
