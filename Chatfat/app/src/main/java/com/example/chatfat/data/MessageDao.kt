package com.example.chatfat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET status = :status WHERE clientMessageId = :clientMessageId")
    suspend fun updateMessageStatus(clientMessageId: String, status: String)
}
