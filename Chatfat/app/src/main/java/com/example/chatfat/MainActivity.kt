package com.example.chatfat

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chatfat.data.ChatfatDatabase
import com.example.chatfat.data.MessageEntity
import com.example.chatfat.ui.theme.ChatfatTheme
import java.util.UUID
import kotlinx.coroutines.launch

private const val SEND_DEBOUNCE_MS = 500L

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatfatTheme {
                ChatScreen()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatScreen() {
    val context = LocalContext.current
    val database = remember { ChatfatDatabase.getInstance(context.applicationContext) }
    val messages by database.messageDao().getAllMessages().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var lastSendTime by remember { mutableLongStateOf(0L) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chatfat") }) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = messages,
                    key = { message -> message.clientMessageId }
                ) { message ->
                    MessageCard(message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Message") },
                    singleLine = true
                )
                Button(
                    enabled = input.isNotBlank(),
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSendTime < SEND_DEBOUNCE_MS) return@Button

                        val message = MessageEntity(
                            clientMessageId = UUID.randomUUID().toString(),
                            text = input.trim(),
                            status = MessageStatus.PENDING.name,
                            createdAt = System.currentTimeMillis()
                        )
                        coroutineScope.launch {
                            database.messageDao().insert(message)
                        }
                        input = ""
                        lastSendTime = now
                    }
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: MessageEntity) {
    val status = MessageStatus.valueOf(message.status)
    val statusLabel = when (status) {
        MessageStatus.PENDING -> "Waiting"
        MessageStatus.SENT -> "Sent"
        MessageStatus.DELIVERED -> "Delivered"
        MessageStatus.FAILED -> "Failed"
    }

    val statusColor = when (status) {
        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        MessageStatus.DELIVERED -> MaterialTheme.colorScheme.secondary
        MessageStatus.PENDING,
        MessageStatus.SENT -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(message.text)
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    ChatfatTheme {
        ChatScreen()
    }
}
