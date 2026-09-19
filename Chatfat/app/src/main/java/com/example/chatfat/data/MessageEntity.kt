package com.example.chatfat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val clientMessageId: String,
    val text: String,
    val status: String,
    val createdAt: Long
)
