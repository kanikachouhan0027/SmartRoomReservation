package com.smartroom.reserve.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.data.floorLabel
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.AmenityRow
import com.smartroom.reserve.ui.components.AvailabilityPill
import com.smartroom.reserve.ui.components.SectionTitle
import com.smartroom.reserve.ui.components.StatusBadge
import com.smartroom.reserve.ui.components.pickDate
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import java.time.LocalDate

@Composable
fun RoomDetailScreen(
    vm: AppViewModel,
    roomId: Long,
    onBack: () -> Unit,
    onBook: (roomId: Long, start: Long, end: Long) -> Unit
) {
    val context = LocalContext.current
    val busyRooms by vm.busyRooms.collectAsState()
    var room by remember { mutableStateOf<Room?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var schedule by remember { mutableStateOf<List<Booking>>(emptyList()) }

    LaunchedEffect(roomId) { room = vm.getRoom(roomId) }
    LaunchedEffect(roomId, date) { schedule = vm.schedule(roomId, date) }

    val today = LocalDate.now()
    val lastDay = today.plusDays(BookingRules.MAX_DAYS_AHEAD.toLong())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "Room") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (room != null) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { onBook(roomId, 0L, 0L) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(52.dp)
                    ) { Text("Book this room") }
                }
            }
        }
    ) { padding ->
        val r = room
        if (r == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    r.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                AvailabilityPill(r.id !in busyRooms)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("${r.building} • ${floorLabel(r.floor)}")
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Seats up to ${r.capacity} people")
                            }
                            if (r.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    r.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            AmenityRow(r.amenities)
                        }
                    }
                }

                // Date selector
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { date = date.minusDays(1) }, enabled = date.isAfter(today)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                        }
                        TextButton(
                            onClick = { pickDate(context, date) { date = it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (date == today) "Today, ${TimeUtils.shortDateLabel(date)}" else TimeUtils.dateLabel(date),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { date = date.plusDays(1) }, enabled = date.isBefore(lastDay)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }
                }

                item { SectionTitle("Reservations on this day") }
                if (schedule.isEmpty()) {
                    item {
                        Text(
                            "No reservations yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(schedule, key = { it.id }) { b ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(TimeUtils.rangeLabel(b.start, b.end), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${b.purpose} • ${b.userName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(b.status)
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
