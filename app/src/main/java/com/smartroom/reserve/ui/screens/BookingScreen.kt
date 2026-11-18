package com.smartroom.reserve.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.data.floorLabel
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.PickerField
import com.smartroom.reserve.ui.components.pickDate
import com.smartroom.reserve.ui.components.pickTime
import com.smartroom.reserve.ui.theme.SuccessGreen
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun BookingScreen(
    vm: AppViewModel,
    roomId: Long,
    initialStart: Long,
    initialEnd: Long,
    initialAttendees: Int,
    initialPurpose: String = "",
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var room by remember { mutableStateOf<Room?>(null) }
    LaunchedEffect(roomId) { room = vm.getRoom(roomId) }

    val defaultStart = remember {
        if (initialStart > 0) TimeUtils.toLocalDateTime(initialStart) else TimeUtils.nextSlot(LocalDateTime.now())
    }
    val defaultEnd = remember {
        if (initialEnd > initialStart && initialStart > 0) TimeUtils.toLocalDateTime(initialEnd)
        else defaultStart.plusHours(1)
    }

    var date by rememberSaveable { mutableStateOf(defaultStart.toLocalDate()) }
    var startTime by rememberSaveable { mutableStateOf(defaultStart.toLocalTime()) }
    var endTime by rememberSaveable { mutableStateOf(defaultEnd.toLocalTime()) }
    var purpose by rememberSaveable { mutableStateOf(initialPurpose) }
    var attendees by rememberSaveable { mutableStateOf(initialAttendees.coerceAtLeast(1).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var isFree by remember { mutableStateOf<Boolean?>(null) }

    val startMs = TimeUtils.toMillis(date, startTime)
    val endMs = TimeUtils.toMillis(date, endTime)

    // Live availability check whenever the time changes.
    LaunchedEffect(startMs, endMs) {
        isFree = if (endMs > startMs) vm.isRoomFree(roomId, startMs, endMs) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New booking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            room?.let { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            r.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${r.building} • ${floorLabel(r.floor)} • up to ${r.capacity} people",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            PickerField(
                label = "Date",
                value = TimeUtils.dateLabel(date),
                icon = Icons.Filled.DateRange,
                modifier = Modifier.fillMaxWidth()
            ) { pickDate(context, date) { date = it } }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = "Start",
                    value = TimeUtils.timeLabel(startTime),
                    icon = Icons.Filled.Edit,
                    modifier = Modifier.weight(1f)
                ) {
                    pickTime(context, startTime) { picked ->
                        // keep the same duration when the start moves
                        val duration = java.time.Duration.between(startTime, endTime)
                        startTime = picked
                        val newEnd = picked.plus(if (duration.isNegative || duration.isZero) java.time.Duration.ofHours(1) else duration)
                        endTime = if (newEnd.isBefore(picked)) LocalTime.of(23, 59) else newEnd
                    }
                }
                PickerField(
                    label = "End",
                    value = TimeUtils.timeLabel(endTime),
                    icon = Icons.Filled.Edit,
                    modifier = Modifier.weight(1f)
                ) { pickTime(context, endTime) { endTime = it } }
            }

            Text("Quick duration", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30L to "30 min", 60L to "1 hr", 90L to "1.5 hr", 120L to "2 hr").forEach { (mins, label) ->
                    AssistChip(
                        onClick = {
                            val e = startTime.plusMinutes(mins)
                            if (!e.isBefore(startTime)) endTime = e
                        },
                        label = { Text(label) }
                    )
                }
            }

            // Availability indicator
            when (isFree) {
                true -> StatusLine(Icons.Filled.CheckCircle, "Room is free for this time", SuccessGreen)
                false -> StatusLine(Icons.Filled.Warning, "Already booked for part of this time", MaterialTheme.colorScheme.error)
                null -> {}
            }

            OutlinedTextField(
                value = purpose,
                onValueChange = { purpose = it.take(80) },
                label = { Text("Purpose (e.g. Project review)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = attendees,
                onValueChange = { v -> attendees = v.filter { it.isDigit() }.take(4) },
                label = { Text("Number of attendees") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { room?.let { Text("Max ${it.capacity} for this room") } },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Rules: ${BookingRules.OPEN_HOUR}:00 AM – 9:00 PM, ${BookingRules.MIN_MINUTES} min to " +
                    "${BookingRules.MAX_MINUTES / 60} hrs, up to ${BookingRules.MAX_DAYS_AHEAD} days ahead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    submitting = true
                    error = null
                    vm.book(roomId, startMs, endMs, purpose, attendees.toIntOrNull() ?: 0) { err ->
                        submitting = false
                        if (err == null) onDone() else { error = err }
                    }
                },
                enabled = !submitting && room != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Confirm booking")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold)
    }
}
