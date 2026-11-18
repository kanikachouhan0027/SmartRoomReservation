package com.smartroom.reserve.data

import android.content.ContentValues
import android.database.Cursor
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.Security
import com.smartroom.reserve.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Repository = the only class that talks to the database.
 * Screens never write SQL; they ask the ViewModel, which asks the Repository.
 *
 * Every function runs on Dispatchers.IO (a background thread) so the UI never freezes.
 * Functions that change data return an error message, or null if everything went fine.
 */
class Repository(private val db: DbHelper) {

    // ---------- small helpers to read a row from a Cursor ----------

    private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.lng(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))

    private fun Cursor.toUser() = User(lng("id"), str("name"), str("email"), int("is_admin") == 1)

    private fun Cursor.toRoom() = Room(
        id = lng("id"),
        name = str("name"),
        building = str("building"),
        floor = int("floor"),
        capacity = int("capacity"),
        amenities = str("amenities").split(DbHelper.AMENITY_SEP).filter { it.isNotBlank() },
        description = str("description")
    )

    private fun Cursor.toBooking() = Booking(
        id = lng("id"),
        roomId = lng("room_id"),
        roomName = str("room_name"),
        userId = lng("user_id"),
        userName = str("user_name"),
        start = lng("start_time"),
        end = lng("end_time"),
        purpose = str("purpose"),
        attendees = int("attendees"),
        status = if (str("status") == BookingStatus.CANCELLED.name) BookingStatus.CANCELLED else BookingStatus.BOOKED,
        createdAt = lng("created_at")
    )

    /** Runs a SELECT and turns every row into an object using [map]. */
    private fun <T> query(sql: String, args: Array<String>?, map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(map(c))
        }
        return out
    }

    private fun count(sql: String, args: Array<String>?): Int =
        query(sql, args) { it.getInt(0) }.firstOrNull() ?: 0

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---------- users ----------

    suspend fun register(name: String, email: String, password: String): Result<User> = io {
        val e = email.trim().lowercase()
        if (count("SELECT COUNT(*) FROM users WHERE email = ?", arrayOf(e)) > 0) {
            Result.failure<User>(IllegalArgumentException("An account with this email already exists"))
        } else {
            val cv = ContentValues().apply {
                put("name", name.trim())
                put("email", e)
                put("password_hash", Security.hash(e, password))
                put("is_admin", 0)
            }
            val id = db.writableDatabase.insert("users", null, cv)
            if (id == -1L) Result.failure<User>(IllegalStateException("Could not create account"))
            else Result.success(User(id, name.trim(), e, false))
        }
    }

    suspend fun login(email: String, password: String): User? = io {
        val e = email.trim().lowercase()
        query(
            "SELECT * FROM users WHERE email = ? AND password_hash = ?",
            arrayOf(e, Security.hash(e, password))
        ) { it.toUser() }.firstOrNull()
    }

    suspend fun getUser(id: Long): User? = io {
        query("SELECT * FROM users WHERE id = ?", arrayOf(id.toString())) { it.toUser() }.firstOrNull()
    }

    // ---------- rooms ----------

    private fun roomsSync(): List<Room> =
        query("SELECT * FROM rooms WHERE active = 1 ORDER BY building, floor, name", null) { it.toRoom() }

    private fun roomSync(id: Long): Room? =
        query("SELECT * FROM rooms WHERE id = ?", arrayOf(id.toString())) { it.toRoom() }.firstOrNull()

    suspend fun getRooms(): List<Room> = io { roomsSync() }

    suspend fun getRoom(id: Long): Room? = io { roomSync(id) }

    suspend fun addRoom(
        name: String, building: String, floor: Int, capacity: Int,
        amenities: List<String>, description: String
    ): String? = io {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("building", building.trim())
            put("floor", floor)
            put("capacity", capacity)
            put("amenities", amenities.joinToString(DbHelper.AMENITY_SEP))
            put("description", description.trim())
            put("active", 1)
        }
        if (db.writableDatabase.insert("rooms", null, cv) == -1L) "Could not add room" else null
    }

    /** "Soft delete": the room is hidden (active = 0) so old bookings still show its name. */
    suspend fun deleteRoom(id: Long, now: Long): String? = io {
        val w = db.writableDatabase
        w.execSQL("UPDATE rooms SET active = 0 WHERE id = ?", arrayOf<Any>(id))
        // cancel the room's future bookings
        w.execSQL(
            "UPDATE bookings SET status = 'CANCELLED' WHERE room_id = ? AND start_time > ?",
            arrayOf<Any>(id, now)
        )
        null
    }

    // ---------- bookings ----------

    // Joins bookings with rooms and users so each booking also has the room name and user name.
    private val bookingSelect = """
        SELECT b.*, r.name AS room_name, u.name AS user_name
        FROM bookings b
        JOIN rooms r ON r.id = b.room_id
        JOIN users u ON u.id = b.user_id
    """.trimIndent()

    /**
     * Clash check — the heart of the app.
     * Two bookings overlap when (existing.start < new.end) AND (existing.end > new.start).
     */
    private fun hasClash(roomId: Long, start: Long, end: Long): Boolean =
        count(
            "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND status = 'BOOKED' " +
                "AND start_time < ? AND end_time > ?",
            arrayOf(roomId.toString(), end.toString(), start.toString())
        ) > 0

    suspend fun isRoomFree(roomId: Long, start: Long, end: Long): Boolean =
        io { !hasClash(roomId, start, end) }

    /** Bookings of one room on one day (for the room's schedule). */
    suspend fun roomSchedule(roomId: Long, date: LocalDate): List<Booking> = io {
        val (dayStart, dayEnd) = TimeUtils.dayBounds(date)
        query(
            "$bookingSelect WHERE b.room_id = ? AND b.status = 'BOOKED' " +
                "AND b.start_time < ? AND b.end_time > ? ORDER BY b.start_time",
            arrayOf(roomId.toString(), dayEnd.toString(), dayStart.toString())
        ) { it.toBooking() }
    }

    suspend fun createBooking(
        userId: Long, roomId: Long, start: Long, end: Long,
        purpose: String, attendees: Int, now: Long
    ): String? = io {
        // 1. room must exist
        val room = roomSync(roomId) ?: return@io "Room not found"
        // 2. purpose must be filled
        if (purpose.isBlank()) return@io "Please enter the purpose of the booking"
        // 3. time and capacity rules
        val ruleError = BookingRules.validate(start, end, attendees, room.capacity, now)
        if (ruleError != null) return@io ruleError
        // 4. no double booking
        if (hasClash(roomId, start, end)) return@io "This room is already booked for part of that time"
        // 5. save
        val cv = ContentValues().apply {
            put("room_id", roomId)
            put("user_id", userId)
            put("start_time", start)
            put("end_time", end)
            put("purpose", purpose.trim())
            put("attendees", attendees)
            put("status", BookingStatus.BOOKED.name)
            put("created_at", now)
        }
        if (db.writableDatabase.insert("bookings", null, cv) == -1L) "Could not save booking" else null
    }

    suspend fun userBookings(userId: Long): List<Booking> = io {
        query("$bookingSelect WHERE b.user_id = ? ORDER BY b.start_time DESC", arrayOf(userId.toString())) {
            it.toBooking()
        }
    }

    suspend fun allBookings(): List<Booking> = io {
        query("$bookingSelect ORDER BY b.start_time DESC", null) { it.toBooking() }
    }

    suspend fun cancelBooking(id: Long): String? = io {
        db.writableDatabase.execSQL(
            "UPDATE bookings SET status = 'CANCELLED' WHERE id = ?",
            arrayOf<Any>(id)
        )
        null
    }

    /** IDs of rooms that have a booking going on right now. */
    suspend fun busyRoomIds(now: Long): Set<Long> = io {
        query(
            "SELECT room_id FROM bookings WHERE status = 'BOOKED' AND start_time <= ? AND end_time > ?",
            arrayOf(now.toString(), now.toString())
        ) { it.getLong(0) }.toSet()
    }

    /** Room finder: rooms big enough, with the needed amenities, and free at that time. Smallest first. */
    suspend fun findAvailableRooms(
        start: Long, end: Long, attendees: Int, amenities: Set<String>
    ): List<Room> = io {
        roomsSync()
            .filter { it.capacity >= attendees }
            .filter { it.amenities.containsAll(amenities) }
            .filter { !hasClash(it.id, start, end) }
            .sortedBy { it.capacity }
    }

    suspend fun adminStats(now: Long): AdminStats = io {
        val (todayStart, todayEnd) = TimeUtils.dayBounds(LocalDate.now())
        AdminStats(
            totalRooms = count("SELECT COUNT(*) FROM rooms WHERE active = 1", null),
            totalUsers = count("SELECT COUNT(*) FROM users", null),
            todayBookings = count(
                "SELECT COUNT(*) FROM bookings WHERE status = 'BOOKED' AND start_time >= ? AND start_time < ?",
                arrayOf(todayStart.toString(), todayEnd.toString())
            ),
            upcomingBookings = count(
                "SELECT COUNT(*) FROM bookings WHERE status = 'BOOKED' AND start_time > ?",
                arrayOf(now.toString())
            )
        )
    }

    /**
     * Plain-text summary of the last 30 days and next 7 days, sent to the AI for admin insights.
     * We send only numbers and room names, never passwords or emails.
     */
    suspend fun usageReport(now: Long): String = io {
        val from = now - 30 * TimeUtils.DAY
        val to = now + 7 * TimeUtils.DAY
        val rooms = roomsSync()
        val bookings = query(
            "$bookingSelect WHERE b.start_time >= ? AND b.start_time < ?",
            arrayOf(from.toString(), to.toString())
        ) { it.toBooking() }

        val sb = StringBuilder()
        sb.append("Period: last 30 days and next 7 days. Total bookings: ${bookings.size}.\n")
        sb.append("Rooms (name | capacity | bookings | booked hours | average group size | cancelled):\n")
        for (room in rooms) {
            val list = bookings.filter { it.roomId == room.id }
            val active = list.filter { it.status == BookingStatus.BOOKED }
            val hours = active.sumOf { (it.end - it.start).toDouble() / TimeUtils.HOUR }
            val avg = if (active.isEmpty()) 0 else active.sumOf { it.attendees } / active.size
            val cancelled = list.count { it.status == BookingStatus.CANCELLED }
            sb.append("${room.name} | ${room.capacity} | ${active.size} | ${"%.1f".format(hours)} | $avg | $cancelled\n")
        }
        sb.append("Bookings by start hour: ")
        val byHour = bookings.filter { it.status == BookingStatus.BOOKED }
            .groupBy { TimeUtils.toLocalDateTime(it.start).hour }
            .toSortedMap()
            .map { (h, l) -> "$h:00=${l.size}" }
        sb.append(if (byHour.isEmpty()) "none" else byHour.joinToString(", "))
        sb.toString()
    }
}
