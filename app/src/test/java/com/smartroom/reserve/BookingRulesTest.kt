package com.smartroom.reserve

import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BookingRulesTest {

    private val day: LocalDate = LocalDate.now().plusDays(2)
    private val now = TimeUtils.toMillis(LocalDate.now(), LocalTime.of(9, 0))
    private fun at(h: Int, m: Int = 0) = TimeUtils.toMillis(day, LocalTime.of(h, m))

    @Test fun validBookingPasses() =
        assertNull(BookingRules.validate(at(10), at(11), 5, 10, now))

    @Test fun endBeforeStartFails() =
        assertNotNull(BookingRules.validate(at(11), at(10), 5, 10, now))

    @Test fun tooShortFails() =
        assertNotNull(BookingRules.validate(at(10), at(10, 10), 5, 10, now))

    @Test fun tooLongFails() =
        assertNotNull(BookingRules.validate(at(9), at(14), 5, 10, now))

    @Test fun overCapacityFails() =
        assertEquals("This room fits only 10 people", BookingRules.validate(at(10), at(11), 11, 10, now))

    @Test fun beforeOpeningFails() =
        assertNotNull(BookingRules.validate(at(7), at(8, 30), 2, 10, now))

    @Test fun afterClosingFails() =
        assertNotNull(BookingRules.validate(at(20, 30), at(21, 30), 2, 10, now))

    @Test fun endingExactlyAtClosingPasses() =
        assertNull(BookingRules.validate(at(20), at(21), 2, 10, now))

    @Test fun pastTimeFails() {
        val yesterday = LocalDate.now().minusDays(1)
        val s = TimeUtils.toMillis(yesterday, LocalTime.of(10, 0))
        assertNotNull(BookingRules.validate(s, s + TimeUtils.HOUR, 2, 10, System.currentTimeMillis()))
    }

    @Test fun overlapDetection() {
        assertTrue(BookingRules.overlaps(at(10), at(11), at(10, 30), at(12)))
        assertFalse(BookingRules.overlaps(at(10), at(11), at(11), at(12))) // back-to-back is fine
    }

    @Test fun nextSlotRespectsOpeningHours() {
        val early = TimeUtils.nextSlot(LocalDateTime.of(2030, 1, 1, 6, 10))
        assertEquals(LocalDateTime.of(2030, 1, 1, 8, 0), early)
        val late = TimeUtils.nextSlot(LocalDateTime.of(2030, 1, 1, 20, 40))
        assertEquals(LocalDateTime.of(2030, 1, 2, 8, 0), late)
        val mid = TimeUtils.nextSlot(LocalDateTime.of(2030, 1, 1, 13, 5))
        assertEquals(LocalDateTime.of(2030, 1, 1, 13, 30), mid)
    }
}
