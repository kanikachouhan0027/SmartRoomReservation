package com.smartroom.reserve.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val shortDateFmt = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    fun toMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun dayBounds(date: LocalDate): Pair<Long, Long> =
        toMillis(date, LocalTime.MIDNIGHT) to toMillis(date.plusDays(1), LocalTime.MIDNIGHT)

    fun dateLabel(millis: Long): String = dateFmt.format(toLocalDateTime(millis))
    fun dateLabel(date: LocalDate): String = dateFmt.format(date)
    fun shortDateLabel(date: LocalDate): String = shortDateFmt.format(date)
    fun timeLabel(millis: Long): String = timeFmt.format(toLocalDateTime(millis))
    fun timeLabel(time: LocalTime): String = timeFmt.format(time)
    fun rangeLabel(start: Long, end: Long): String = "${timeLabel(start)} – ${timeLabel(end)}"

    fun durationLabel(start: Long, end: Long): String {
        val minutes = ((end - start) / MINUTE).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h hr"
            else -> "$h hr $m min"
        }
    }

    /** Next half-hour slot that fits inside opening hours (moves to tomorrow if needed). */
    fun nextSlot(now: LocalDateTime): LocalDateTime {
        var t = now.withSecond(0).withNano(0)
        t = t.plusMinutes((30 - t.minute % 30).toLong())
        if (t.hour < BookingRules.OPEN_HOUR) {
            t = t.toLocalDate().atTime(BookingRules.OPEN_HOUR, 0)
        }
        val lastStart = LocalTime.of(BookingRules.CLOSE_HOUR - 1, 0)
        if (t.toLocalTime().isAfter(lastStart)) {
            t = t.toLocalDate().plusDays(1).atTime(BookingRules.OPEN_HOUR, 0)
        }
        return t
    }
}
