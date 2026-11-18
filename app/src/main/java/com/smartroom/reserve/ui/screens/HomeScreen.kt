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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.smartroom.reserve.data.Amenities
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.EmptyState
import com.smartroom.reserve.ui.components.RoomCard

@Composable
fun HomeScreen(vm: AppViewModel, onRoom: (Long) -> Unit) {
    val rooms by vm.rooms.collectAsState()
    val busyRooms by vm.busyRooms.collectAsState()
    val user by vm.user.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var amenity by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFree by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    val selectedAmenity = amenity
    val filtered = rooms.filter { r ->
        (query.isBlank() || r.name.contains(query, ignoreCase = true) || r.building.contains(query, ignoreCase = true)) &&
            (selectedAmenity == null || r.amenities.contains(selectedAmenity)) &&
            (!onlyFree || r.id !in busyRooms)
    }
    val freeCount = rooms.count { it.id !in busyRooms }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Rooms", fontWeight = FontWeight.Bold)
                        Text(
                            "Hi, ${user?.name ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Live availability",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$freeCount",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "  of ${rooms.size} rooms free right now",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (rooms.isEmpty()) 0f else freeCount / rooms.size.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search rooms or buildings") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = onlyFree,
                            onClick = { onlyFree = !onlyFree },
                            label = { Text("Free now") }
                        )
                    }
                    items(Amenities.ALL) { a ->
                        FilterChip(
                            selected = amenity == a,
                            onClick = { amenity = if (amenity == a) null else a },
                            label = { Text(a) }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { EmptyState("No rooms match your filters", "Try clearing the search or filters.") }
            }
            items(filtered, key = { it.id }) { room ->
                RoomCard(room = room, freeNow = room.id !in busyRooms) { onRoom(room.id) }
            }
        }
    }
}
