package com.smartroom.reserve.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.BookingStatus
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.data.floorLabel
import com.smartroom.reserve.ui.theme.InfoBlue
import com.smartroom.reserve.ui.theme.SuccessGreen
import com.smartroom.reserve.ui.theme.WarnOrange
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime

// ---------- native pickers (simple and stable on every Android version) ----------

fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth
    )
    val now = System.currentTimeMillis()
    dialog.datePicker.minDate = now - 1000
    dialog.datePicker.maxDate = now + BookingRules.MAX_DAYS_AHEAD * TimeUtils.DAY
    dialog.show()
}

fun pickTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPick(LocalTime.of(h, m)) },
        initial.hour, initial.minute, false
    ).show()
}

// ---------- small building blocks ----------

@Composable
fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AvailabilityPill(freeNow: Boolean) {
    if (freeNow) Pill("Free now", SuccessGreen) else Pill("In use", WarnOrange)
}

fun statusColor(status: BookingStatus): Color = when (status) {
    BookingStatus.BOOKED -> InfoBlue
    BookingStatus.CANCELLED -> Color(0xFFC62828)
}

@Composable
fun StatusBadge(status: BookingStatus) = Pill(status.label, statusColor(status))

@Composable
fun AmenityRow(amenities: List<String>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        amenities.forEach { a ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    a,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun Initials(text: String, size: Int = 44) {
    val initials = text.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = if (size > 50) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun RoomCard(room: Room, freeNow: Boolean?, badge: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(room.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${room.building} • ${floorLabel(room.floor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (badge != null) Pill(badge, SuccessGreen)
                else if (freeNow != null) AvailabilityPill(freeNow)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text("Up to ${room.capacity} people", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            AmenityRow(room.amenities)
        }
    }
}

@Composable
fun BookingCard(
    booking: Booking,
    showUser: Boolean,
    hint: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(booking.roomName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        TimeUtils.dateLabel(booking.start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(booking.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DateRange, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${TimeUtils.rangeLabel(booking.start, booking.end)}  (${TimeUtils.durationLabel(booking.start, booking.end)})",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(booking.purpose, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${booking.attendees} attendee(s)" + if (showUser) " • by ${booking.userName}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun EmptyState(title: String, subtitle: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.DateRange, contentDescription = null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
