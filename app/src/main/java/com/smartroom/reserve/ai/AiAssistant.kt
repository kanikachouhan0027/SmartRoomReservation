package com.smartroom.reserve.ai

import com.smartroom.reserve.data.Amenities
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the AI understood from the user's sentence. */
data class AiRequest(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val attendees: Int,
    val amenities: Set<String>,
    val purpose: String
)

/** The AI's answer: a reply to show, and (optionally) a room search to run. */
data class AiAnswer(val reply: String, val request: AiRequest?)

/**
 * Builds the instructions (prompt) we give Gemini and turns its JSON answer into Kotlin objects.
 *
 * Important design idea: the AI only UNDERSTANDS the sentence. It never writes to the database.
 * The app's own code (BookingRules + clash check) still decides if a booking is allowed.
 */
object AiAssistant {

    private val dayFmt = DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH)
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun systemPrompt(now: LocalDateTime, rooms: List<Room>, myBookings: List<Booking>, userName: String): String {
        val roomLines = rooms.joinToString("\n") {
            "- ${it.name} (${it.building}, capacity ${it.capacity}; ${it.amenities.joinToString(", ")})"
        }
        val upcoming = myBookings
            .filter { it.end > System.currentTimeMillis() && it.status.name == "BOOKED" }
            .take(10)
            .joinToString("\n") {
                "- ${it.roomName} on ${TimeUtils.dateLabel(it.start)}, ${TimeUtils.rangeLabel(it.start, it.end)} (${it.purpose})"
            }.ifBlank { "- none" }

        return """
            You are "Room Assistant", the AI helper inside the Smart Room Reservation app of a college.
            You are talking to $userName.
            Today is ${dayFmt.format(now)} and the time now is ${hm.format(now)}.

            Booking rules: rooms are open ${BookingRules.OPEN_HOUR}:00 to ${BookingRules.CLOSE_HOUR}:00,
            a booking is ${BookingRules.MIN_MINUTES} minutes to ${BookingRules.MAX_MINUTES / 60} hours long,
            on a single day, at most ${BookingRules.MAX_DAYS_AHEAD} days ahead.

            Rooms:
            $roomLines

            Allowed amenity names (use exactly these spellings): ${Amenities.ALL.joinToString(", ")}

            The user's upcoming bookings:
            $upcoming

            Always answer with ONLY one JSON object, no other text:
            {
              "reply": "a short, friendly answer in simple English (max 60 words)",
              "action": "FIND_ROOMS" or "NONE",
              "date": "YYYY-MM-DD" or "",
              "start": "HH:mm" (24-hour) or "",
              "end": "HH:mm" (24-hour) or "",
              "attendees": number (0 if unknown),
              "amenities": ["..."],
              "purpose": "short purpose of the meeting" or ""
            }

            Use "FIND_ROOMS" when the user wants to book or find a room AND you know at least the start time.
            If only a start time is given, make the meeting 1 hour long.
            If no date is given, use today if the time is still ahead, otherwise tomorrow.
            Understand words like "tomorrow", "next Monday", "evening" (= 17:00), "morning" (= 10:00), "afternoon" (= 14:00).
            If the start time is missing, use "NONE" and ask for it in "reply".
            For any other question (rules, my bookings, which room is biggest, etc.), use "NONE" and answer in "reply".
            Never invent rooms that are not in the list.
        """.trimIndent()
    }

    /** Converts Gemini's JSON text into an [AiAnswer]. Bad or missing values are handled safely. */
    fun parse(json: String): AiAnswer {
        val clean = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = JSONObject(clean)
        val reply = o.optString("reply").ifBlank { "Here is what I found." }
        if (o.optString("action") != "FIND_ROOMS") return AiAnswer(reply, null)

        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: LocalDate.now()
        val start = parseTime(o.optString("start")) ?: return AiAnswer(reply, null)
        val end = parseTime(o.optString("end")) ?: start.plusHours(1)

        val wanted = mutableSetOf<String>()
        val arr = o.optJSONArray("amenities")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val name = arr.optString(i)
                // keep only amenity names our app knows (ignore anything the AI made up)
                Amenities.ALL.firstOrNull { it.equals(name, ignoreCase = true) }?.let { wanted.add(it) }
            }
        }
        val attendees = o.optInt("attendees", 0).coerceAtLeast(1)
        val purpose = o.optString("purpose").take(80)
        return AiAnswer(reply, AiRequest(date, start, end, attendees, wanted, purpose))
    }

    private fun parseTime(s: String): LocalTime? =
        runCatching { LocalTime.parse(s.trim().padStart(5, '0')) }.getOrNull()

    // ---------------- admin insights ----------------

    const val INSIGHTS_PROMPT = """
        You are a data analyst for a college room booking system.
        Using ONLY the data given, write:
        - 4 or 5 short bullet points with insights (busiest and least used rooms, popular times, cancellations, group size vs room size),
        - then 2 short, practical suggestions for the admin.
        Use simple English and plain text bullets starting with "• ". No headings, no tables, under 170 words.
    """
}
