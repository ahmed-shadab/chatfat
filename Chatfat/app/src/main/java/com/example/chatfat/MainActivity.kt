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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEND_DEBOUNCE_MS = 500L
private const val HEALTH_URL = "http://127.0.0.1:3000/health"
private const val MESSAGES_URL = "http://127.0.0.1:3000/messages"

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
    var backendStatus by remember { mutableStateOf("Not checked") }

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
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        backendStatus = "Checking..."
                        coroutineScope.launch {
                            backendStatus = try {
                                withContext(Dispatchers.IO) { fetchBackendStatus() }
                            } catch (exception: Exception) {
                                "Error: ${exception.message ?: "request failed"}"
                            }
                        }
                    }
                ) {
                    Text("Check Backend")
                }
                Text("Backend: $backendStatus")
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
                            try {
                                val status = withContext(Dispatchers.IO) {
                                    sendMessageToBackend(message)
                                }
                                if (status == MessageStatus.SENT.name) {
                                    database.messageDao().updateMessageStatus(
                                        clientMessageId = message.clientMessageId,
                                        status = status
                                    )
                                }
                            } catch (_: Exception) {
                                // Leave the locally saved message as PENDING for now.
                            }
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

private fun sendMessageToBackend(message: MessageEntity): String {
    val connection = URL(MESSAGES_URL).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Content-Type", "application/json")

        val requestBody = JSONObject()
            .put("clientMessageId", message.clientMessageId)
            .put("text", message.text)
            .toString()
        connection.outputStream.use { output ->
            output.write(requestBody.toByteArray())
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        Regex("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?: throw IllegalStateException("Invalid response")
    } finally {
        connection.disconnect()
    }
}

private fun fetchBackendStatus(): String {
    val connection = URL(HEALTH_URL).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000

        if (connection.responseCode !in 200..299) {
            "Error: HTTP ${connection.responseCode}"
        } else {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val status = Regex("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .find(response)
                ?.groupValues
                ?.get(1)
            status ?: "Error: invalid response"
        }
    } finally {
        connection.disconnect()
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
