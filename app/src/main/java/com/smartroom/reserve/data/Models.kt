package com.smartroom.reserve.data

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val isAdmin: Boolean
)

data class Room(
    val id: Long,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    val amenities: List<String>,
    val description: String
)

enum class BookingStatus(val label: String) {
    BOOKED("Booked"),
    CANCELLED("Cancelled")
}

data class Booking(
    val id: Long,
    val roomId: Long,
    val roomName: String,
    val userId: Long,
    val userName: String,
    val start: Long,
    val end: Long,
    val purpose: String,
    val attendees: Int,
    val status: BookingStatus,
    val createdAt: Long
)

data class AdminStats(
    val totalRooms: Int,
    val totalUsers: Int,
    val todayBookings: Int,
    val upcomingBookings: Int
)

object Amenities {
    val ALL = listOf(
        "Projector",
        "AC",
        "Whiteboard",
        "Smart Board",
        "Video Conferencing",
        "Speaker System",
        "Wi-Fi"
    )
}

fun floorLabel(floor: Int): String = if (floor == 0) "Ground floor" else "Floor $floor"
