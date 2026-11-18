package com.smartroom.reserve.util

/**
 * All booking policies live here so they are easy to explain, change and unit-test.
 */
object BookingRules {
    const val OPEN_HOUR = 8          // rooms open 8:00 AM
    const val CLOSE_HOUR = 21        // rooms close 9:00 PM
    const val MIN_MINUTES = 15
    const val MAX_MINUTES = 240      // 4 hours
    const val MAX_DAYS_AHEAD = 30

    /** Returns an error message, or null when the requested slot is valid. */
    fun validate(start: Long, end: Long, attendees: Int, capacity: Int, now: Long): String? {
        if (end <= start) return "End time must be after start time"
        val minutes = (end - start) / TimeUtils.MINUTE
        if (minutes < MIN_MINUTES) return "A booking must be at least $MIN_MINUTES minutes long"
        if (minutes > MAX_MINUTES) return "A booking cannot be longer than ${MAX_MINUTES / 60} hours"
        if (start < now - TimeUtils.MINUTE) return "You cannot book a time that has already passed"
        if (start > now + MAX_DAYS_AHEAD * TimeUtils.DAY) {
            return "You can book at most $MAX_DAYS_AHEAD days in advance"
        }
        val s = TimeUtils.toLocalDateTime(start)
        val e = TimeUtils.toLocalDateTime(end)
        if (s.toLocalDate() != e.toLocalDate()) return "A booking must start and end on the same day"
        if (s.hour < OPEN_HOUR) return "Rooms open at 8:00 AM"
        if (e.hour > CLOSE_HOUR || (e.hour == CLOSE_HOUR && e.minute > 0)) return "Rooms close at 9:00 PM"
        if (attendees < 1) return "Enter the number of attendees"
        if (attendees > capacity) return "This room fits only $capacity people"
        return null
    }

    /** Two time ranges clash if one starts before the other ends. */
    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        aStart < bEnd && bStart < aEnd
}
