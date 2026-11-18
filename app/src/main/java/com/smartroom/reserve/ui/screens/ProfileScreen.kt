package com.smartroom.reserve.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.BookingStatus
import com.smartroom.reserve.data.User
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.components.Initials
import com.smartroom.reserve.ui.components.Pill
import com.smartroom.reserve.ui.components.SectionTitle
import com.smartroom.reserve.ui.theme.InfoBlue
import com.smartroom.reserve.ui.theme.SuccessGreen
import com.smartroom.reserve.util.BookingRules

@Composable
fun ProfileScreen(vm: AppViewModel, user: User) {
    val bookings by vm.myBookings.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val upcoming = bookings.count { it.status == BookingStatus.BOOKED && it.end > now }
    val cancelled = bookings.count { it.status == BookingStatus.CANCELLED }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Initials(user.name, size = 72)
                    Spacer(Modifier.height(12.dp))
                    Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (user.isAdmin) Pill("Administrator", InfoBlue) else Pill("Member", SuccessGreen)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Total", bookings.size, Modifier.weight(1f))
                MiniStat("Upcoming", upcoming, Modifier.weight(1f))
                MiniStat("Cancelled", cancelled, Modifier.weight(1f))
            }

            AiSettingsCard(vm)

            SectionTitle("Booking rules")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rule("Rooms are open 8:00 AM – 9:00 PM.")
                    Rule("Bookings can be ${BookingRules.MIN_MINUTES} minutes to ${BookingRules.MAX_MINUTES / 60} hours long.")
                    Rule("Book up to ${BookingRules.MAX_DAYS_AHEAD} days in advance.")
                    Rule("The number of people must fit the room's capacity.")
                    Rule("A room can never be booked twice for the same time.")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Text("  Log out")
            }
            Text(
                "Smart Room Reservation v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Stay") } }
        )
    }
}

@Composable
private fun MiniStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Rule(text: String) {
    Row {
        Text("•  ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Where the user pastes the free Gemini API key. It is saved only on this phone. */
@Composable
private fun AiSettingsCard(vm: AppViewModel) {
    val savedKey by vm.aiKey.collectAsState()
    val savedModel by vm.aiModel.collectAsState()
    var key by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
    var model by rememberSaveable(savedModel) { mutableStateOf(savedModel) }

    SectionTitle("AI settings (Google Gemini)")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (savedKey.isBlank()) "AI is OFF. Get a free key at aistudio.google.com → Get API key, then paste it here."
                else "AI is ON ✓",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model name") },
                supportingText = { Text("Default: ${AppViewModel.DEFAULT_MODEL}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.saveAiSettings(key, model) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save AI settings")
            }
        }
    }
}
