package com.smartroom.reserve.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.ai.AiRequest
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.util.TimeUtils

private val EXAMPLES = listOf(
    "Book a room for 6 people tomorrow at 3 pm with a projector",
    "I need a quiet room for 2 hours this evening",
    "Which is the biggest room?",
    "Show my upcoming bookings",
    "What are the booking rules?"
)

/**
 * AI Assistant: the user types (or speaks) a sentence; Gemini understands it,
 * and the app shows free rooms with a Book button.
 */
@Composable
fun AssistantScreen(
    vm: AppViewModel,
    onBook: (roomId: Long, start: Long, end: Long, attendees: Int, purpose: String) -> Unit
) {
    val chat by vm.chat.collectAsState()
    val busy by vm.aiBusy.collectAsState()
    val key by vm.aiKey.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Voice input: opens Google's speech-to-text screen and puts the result in the text box.
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) input = heard
        }
    }

    fun send() {
        vm.askAssistant(input)
        input = ""
    }

    LaunchedEffect(chat.size, busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Room Assistant", fontWeight = FontWeight.Bold)
                        Text(
                            if (key.isBlank()) "Not set up — add API key in Profile" else "Powered by Google Gemini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearChat() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear chat")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(chat) { _, msg ->
                    Bubble(msg, onBook)
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Thinking…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Example prompts (shown until the user sends something)
            if (chat.size <= 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EXAMPLES) { ex ->
                        AssistChip(onClick = { input = ex }, label = { Text(ex) })
                    }
                }
            }

            // Input row: text box + mic + send
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type or speak your request…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your booking request")
                    try {
                        speech.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        input = "Voice input is not available on this phone"
                    }
                }) {
                    Text("🎤", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { send() }, enabled = input.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun Bubble(
    msg: AppViewModel.ChatMessage,
    onBook: (Long, Long, Long, Int, String) -> Unit
) {
    val mine = msg.fromUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    mine -> MaterialTheme.colorScheme.primary
                    msg.isError -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    msg.text,
                    color = when {
                        mine -> MaterialTheme.colorScheme.onPrimary
                        msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
            val req = msg.request
            if (req != null) {
                msg.rooms.forEachIndexed { i, room ->
                    Spacer(Modifier.height(6.dp))
                    SuggestedRoom(room, req, best = i == 0, onBook = onBook)
                }
            }
        }
    }
}

@Composable
private fun SuggestedRoom(
    room: Room,
    req: AiRequest,
    best: Boolean,
    onBook: (Long, Long, Long, Int, String) -> Unit
) {
    val start = TimeUtils.toMillis(req.date, req.start)
    val end = TimeUtils.toMillis(req.date, req.end)
    OutlinedCard(modifier = Modifier.widthIn(max = 300.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    room.name + if (best) "  ★ Best fit" else "",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${room.building} • up to ${room.capacity} people",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { onBook(room.id, start, end, req.attendees, req.purpose) }) { Text("Book") }
        }
    }
}
