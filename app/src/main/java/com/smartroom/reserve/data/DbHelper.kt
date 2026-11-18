package com.smartroom.reserve.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smartroom.reserve.util.Security
import com.smartroom.reserve.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime

/**
 * Local SQLite database. Three tables: users, rooms, bookings.
 * Seeded with demo accounts and rooms on first launch so the app is usable immediately.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE rooms(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                building TEXT NOT NULL,
                floor INTEGER NOT NULL,
                capacity INTEGER NOT NULL,
                amenities TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookings(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                room_id INTEGER NOT NULL REFERENCES rooms(id),
                user_id INTEGER NOT NULL REFERENCES users(id),
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                purpose TEXT NOT NULL,
                attendees INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_bookings_room_time ON bookings(room_id, start_time, end_time)")
        db.execSQL("CREATE INDEX idx_bookings_user ON bookings(user_id)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS bookings")
        db.execSQL("DROP TABLE IF EXISTS rooms")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val adminId = insertUser(db, "Admin", "admin@campus.edu", "admin123", true)
        val studentId = insertUser(db, "Demo Student", "student@campus.edu", "student123", false)

        val confA = insertRoom(
            db, "Conference Room A", "Admin Block", 1, 12,
            listOf("Projector", "AC", "Whiteboard", "Video Conferencing", "Wi-Fi"),
            "Medium meeting room with a 65\" display and video-call setup."
        )
        insertRoom(
            db, "Conference Room B", "Admin Block", 2, 8,
            listOf("AC", "Whiteboard", "Wi-Fi"),
            "Quiet room for small team meetings."
        )
        val seminar = insertRoom(
            db, "Seminar Hall", "Academic Block", 0, 120,
            listOf("Projector", "AC", "Speaker System", "Wi-Fi"),
            "Large hall for lectures, seminars and events."
        )
        val disc1 = insertRoom(
            db, "Discussion Room 1", "Library", 1, 4,
            listOf("Whiteboard", "Wi-Fi"),
            "Small glass room for group study."
        )
        insertRoom(
            db, "Discussion Room 2", "Library", 1, 6,
            listOf("Whiteboard", "Smart Board", "Wi-Fi"),
            "Group study room with a smart board."
        )
        insertRoom(
            db, "Computer Lab 3", "IT Block", 2, 40,
            listOf("Projector", "AC", "Smart Board", "Wi-Fi"),
            "40 workstations; ideal for workshops and practicals."
        )
        insertRoom(
            db, "Board Room", "Admin Block", 3, 16,
            listOf("Projector", "AC", "Video Conferencing", "Speaker System", "Wi-Fi"),
            "Formal board room for reviews and panel meetings."
        )
        insertRoom(
            db, "Innovation Lab", "IT Block", 3, 20,
            listOf("Smart Board", "Whiteboard", "AC", "Wi-Fi"),
            "Flexible space for hackathons and project work."
        )

        // A few upcoming bookings so schedules are not empty on first launch.
        val tomorrow = LocalDate.now().plusDays(1)
        val dayAfter = LocalDate.now().plusDays(2)
        insertBooking(db, confA, adminId, tomorrow, 10, 0, 11, 0, "Faculty meeting", 10)
        insertBooking(db, seminar, adminId, tomorrow, 14, 0, 16, 0, "Guest lecture on AI", 100)
        insertBooking(db, disc1, studentId, dayAfter, 11, 0, 12, 30, "Project group discussion", 4)
    }

    private fun insertUser(db: SQLiteDatabase, name: String, email: String, password: String, admin: Boolean): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("email", email)
            put("password_hash", Security.hash(email, password))
            put("is_admin", if (admin) 1 else 0)
        }
        return db.insert("users", null, cv)
    }

    private fun insertRoom(
        db: SQLiteDatabase, name: String, building: String, floor: Int, capacity: Int,
        amenities: List<String>, description: String
    ): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("building", building)
            put("floor", floor)
            put("capacity", capacity)
            put("amenities", amenities.joinToString(AMENITY_SEP))
            put("description", description)
            put("active", 1)
        }
        return db.insert("rooms", null, cv)
    }

    private fun insertBooking(
        db: SQLiteDatabase, roomId: Long, userId: Long, date: LocalDate,
        sh: Int, sm: Int, eh: Int, em: Int, purpose: String, attendees: Int
    ) {
        val cv = ContentValues().apply {
            put("room_id", roomId)
            put("user_id", userId)
            put("start_time", TimeUtils.toMillis(date, LocalTime.of(sh, sm)))
            put("end_time", TimeUtils.toMillis(date, LocalTime.of(eh, em)))
            put("purpose", purpose)
            put("attendees", attendees)
            put("status", BookingStatus.BOOKED.name)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("bookings", null, cv)
    }

    companion object {
        const val DB_NAME = "smartroom.db"
        const val DB_VERSION = 1
        const val AMENITY_SEP = "|"
    }
}
