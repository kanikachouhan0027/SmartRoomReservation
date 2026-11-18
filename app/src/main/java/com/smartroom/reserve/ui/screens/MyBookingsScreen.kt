package com.smartroom.reserve.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.BookingStatus
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.BookingCard
import com.smartroom.reserve.ui.components.EmptyState
import com.smartroom.reserve.util.TimeUtils

@Composable
fun MyBookingsScreen(vm: AppViewModel) {
    val bookings by vm.myBookings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var toCancel by remember { mutableStateOf<Booking?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    val now = System.currentTimeMillis()
    // Upcoming = still booked and not finished yet. Everything else is history.
    val upcoming = bookings.filter { it.status == BookingStatus.BOOKED && it.end > now }.sortedBy { it.start }
    val past = bookings.filter { it !in upcoming }.sortedByDescending { it.start }

    Scaffold(topBar = { TopAppBar(title = { Text("My Bookings") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Upcoming (${upcoming.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${past.size})") })
            }
            val list = if (tab == 0) upcoming else past
            if (list.isEmpty()) {
                EmptyState(
                    if (tab == 0) "No upcoming bookings" else "No past bookings",
                    if (tab == 0) "Browse rooms or use Find to reserve a space." else ""
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.id }) { b ->
                        BookingCard(booking = b, showUser = false) {
                            // only upcoming bookings can be cancelled
                            if (tab == 0) {
                                TextButton(
                                    onClick = { toCancel = b },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }

    toCancel?.let { b ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("Cancel booking?") },
            text = { Text("${b.roomName}\n${TimeUtils.dateLabel(b.start)}, ${TimeUtils.rangeLabel(b.start, b.end)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancel(b)
                    toCancel = null
                }) { Text("Yes, cancel") }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("Keep it") } }
        )
    }
}
