package com.smartroom.reserve.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.AdminStats
import com.smartroom.reserve.data.Amenities
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.BookingStatus
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.BookingCard
import com.smartroom.reserve.ui.components.EmptyState
import com.smartroom.reserve.ui.components.RoomCard

@Composable
fun AdminScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsState()
    val rooms by vm.rooms.collectAsState()
    val busyRooms by vm.busyRooms.collectAsState()
    val bookings by vm.allBookings.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var roomToDelete by remember { mutableStateOf<Room?>(null) }
    var bookingToCancel by remember { mutableStateOf<Booking?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin dashboard") }) },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add room") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overview") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Rooms") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Bookings") })
            }
            when (tab) {
                0 -> Overview(stats, vm)
                1 -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (rooms.isEmpty()) item { EmptyState("No rooms yet", "Tap “Add room” to create one.") }
                    items(rooms, key = { it.id }) { room ->
                        Column {
                            RoomCard(room = room, freeNow = room.id !in busyRooms) { }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { roomToDelete = room },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                    Text(" Remove")
                                }
                            }
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bookings.isEmpty()) item { EmptyState("No bookings yet") }
                    items(bookings, key = { it.id }) { b ->
                        BookingCard(booking = b, showUser = true) {
                            if (b.status == BookingStatus.BOOKED) {
                                TextButton(
                                    onClick = { bookingToCancel = b },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Cancel booking") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRoomDialog(
            onDismiss = { showAdd = false },
            onSave = { name, building, floor, capacity, amenities, description ->
                vm.addRoom(name, building, floor, capacity, amenities, description)
                showAdd = false
            }
        )
    }

    roomToDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = { Text("Remove ${room.name}?") },
            text = { Text("The room will be hidden and its upcoming bookings cancelled. Past history is kept.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRoom(room); roomToDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { roomToDelete = null }) { Text("Keep") } }
        )
    }

    bookingToCancel?.let { b ->
        AlertDialog(
            onDismissRequest = { bookingToCancel = null },
            title = { Text("Cancel this booking?") },
            text = { Text("${b.roomName} booked by ${b.userName} for “${b.purpose}”.") },
            confirmButton = {
                TextButton(onClick = { vm.cancel(b); bookingToCancel = null }) { Text("Cancel booking") }
            },
            dismissButton = { TextButton(onClick = { bookingToCancel = null }) { Text("Back") } }
        )
    }
}

@Composable
private fun Overview(stats: AdminStats?, vm: AppViewModel) {
    val insights by vm.insights.collectAsState()
    val busy by vm.aiBusy.collectAsState()
    if (stats == null) {
        EmptyState("Loading statistics…")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Rooms", stats.totalRooms.toString(), Modifier.weight(1f))
                StatCard("Users", stats.totalUsers.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Today's bookings", stats.todayBookings.toString(), Modifier.weight(1f))
                StatCard("Upcoming", stats.upcomingBookings.toString(), Modifier.weight(1f))
            }
        }
        // AI insights: Gemini reads the usage numbers and explains them in plain English
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI usage insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Gemini studies the last 30 days of bookings and suggests improvements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { vm.generateInsights() }, enabled = !busy) {
                        Text(if (busy) "Analysing…" else "Generate AI insights")
                    }
                    insights?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AddRoomDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, building: String, floor: Int, capacity: Int, amenities: List<String>, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("0") }
    var capacity by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amenities by remember { mutableStateOf(setOf("Wi-Fi")) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a room") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Room name") }, singleLine = true)
                OutlinedTextField(building, { building = it }, label = { Text("Building") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        floor, { v -> floor = v.filter { it.isDigit() }.take(2) },
                        label = { Text("Floor") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        capacity, { v -> capacity = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Capacity") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") })
                Text("Amenities", style = MaterialTheme.typography.labelLarge)
                Amenities.ALL.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = a in amenities,
                            onCheckedChange = { checked -> amenities = if (checked) amenities + a else amenities - a }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(a)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cap = capacity.toIntOrNull() ?: 0
                val fl = floor.toIntOrNull() ?: 0
                error = when {
                    name.isBlank() -> "Enter a room name"
                    building.isBlank() -> "Enter the building"
                    cap < 1 -> "Capacity must be at least 1"
                    else -> null
                }
                if (error == null) {
                    onSave(name, building, fl, cap, Amenities.ALL.filter { it in amenities }, description)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
