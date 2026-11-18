package com.smartroom.reserve.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.Amenities
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.EmptyState
import com.smartroom.reserve.ui.components.PickerField
import com.smartroom.reserve.ui.components.RoomCard
import com.smartroom.reserve.ui.components.SectionTitle
import com.smartroom.reserve.ui.components.pickDate
import com.smartroom.reserve.ui.components.pickTime
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Smart finder: user says when, how many people and what they need;
 * the app returns only free rooms, smallest suitable room first ("best fit").
 */
@Composable
fun FindRoomScreen(
    vm: AppViewModel,
    onBook: (roomId: Long, start: Long, end: Long, attendees: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { TimeUtils.nextSlot(LocalDateTime.now()) }

    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var startTime by rememberSaveable { mutableStateOf(initial.toLocalTime()) }
    var endTime by rememberSaveable { mutableStateOf(initial.toLocalTime().plusHours(1)) }
    var attendees by rememberSaveable { mutableStateOf("2") }
    var needs by remember { mutableStateOf(setOf<String>()) }
    var results by remember { mutableStateOf<List<Room>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val startMs = TimeUtils.toMillis(date, startTime)
    val endMs = TimeUtils.toMillis(date, endTime)
    val people = attendees.toIntOrNull() ?: 0

    Scaffold(topBar = { TopAppBar(title = { Text("Find a free room") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PickerField("Date", TimeUtils.dateLabel(date), Icons.Filled.DateRange, Modifier.fillMaxWidth()) {
                    pickDate(context, date) { date = it; results = null }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField("From", TimeUtils.timeLabel(startTime), Icons.Filled.Edit, Modifier.weight(1f)) {
                        pickTime(context, startTime) {
                            startTime = it
                            if (!endTime.isAfter(it)) endTime = it.plusHours(1)
                            results = null
                        }
                    }
                    PickerField("To", TimeUtils.timeLabel(endTime), Icons.Filled.Edit, Modifier.weight(1f)) {
                        pickTime(context, endTime) { endTime = it; results = null }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = attendees,
                    onValueChange = { v -> attendees = v.filter { it.isDigit() }.take(4); results = null },
                    label = { Text("How many people?") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Text("Must have", style = MaterialTheme.typography.labelLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Amenities.ALL) { a ->
                        FilterChip(
                            selected = a in needs,
                            onClick = {
                                needs = if (a in needs) needs - a else needs + a
                                results = null
                            },
                            label = { Text(a) }
                        )
                    }
                }
            }
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        error = BookingRules.validate(startMs, endMs, people, Int.MAX_VALUE, System.currentTimeMillis())
                        if (error == null) {
                            scope.launch { results = vm.findRooms(startMs, endMs, people, needs) }
                        } else {
                            results = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  Search available rooms")
                }
              }
            }

            val found = results
            if (found != null) {
                item {
                    SectionTitle(
                        if (found.isEmpty()) "No rooms free" else "${found.size} room(s) free • best fit first"
                    )
                }
                if (found.isEmpty()) {
                    item { EmptyState("Nothing matches", "Try another time, fewer amenities or a smaller group.") }
                }
                items(found, key = { it.id }) { room ->
                    RoomCard(
                        room = room,
                        freeNow = null,
                        badge = if (room == found.first()) "Best fit" else null
                    ) { onBook(room.id, startMs, endMs, people) }
                }
                if (found.isNotEmpty()) {
                    item {
                        Text(
                            "Tap a room to book it for ${TimeUtils.rangeLabel(startMs, endMs)}.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
