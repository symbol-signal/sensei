package symsig.sensei

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import symsig.sensei.util.schedule.RampScheduler
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RampSchedulerTest {

    @Test
    fun `should throw exception when start and end times are identical`() {
        val exception = assertThrows<IllegalArgumentException> {
            RampScheduler(
                timeBounds = LocalTime.of(10, 0)..LocalTime.of(10, 0),
                values = 0..100,
                callback = {}
            )
        }
        assertEquals("Time bounds must be distinctive values", exception.message)
    }

    @Test
    fun `same-day cycle - before start time should return no update and delay until start`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {},
            timeProvider = { LocalDateTime.of(2025, 1, 1, 9, 30, 0) }
        )

        val (update, delay) = timer.runCycle(LocalDateTime.of(2025, 1, 1, 9, 30, 0))

        assertNull(update)
        assertEquals(1800_000L, delay) // 30 minutes in millis
    }

    @Test
    fun `same-day cycle - at exact start time should return first value`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        val now = LocalDateTime.of(2025, 1, 1, 10, 0, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(0, update.value)
        assertEquals(now, update.updateTime)
    }

    @Test
    fun `same-day cycle - at exact end time should return last value if not already set`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        val now = LocalDateTime.of(2025, 1, 1, 14, 0, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(100, update.value)
        assertEquals(now, update.updateTime)
    }

    @Test
    fun `same-day cycle - middle of cycle should calculate correct value`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        // At 12:00, we're 2 hours into a 4-hour cycle, so should be at 50%
        val now = LocalDateTime.of(2025, 1, 1, 12, 0, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(50, update.value)
    }

    @Test
    fun `same-day cycle - decreasing values should work correctly`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 100 downTo 0,
            callback = {}
        )

        // At 12:00, we're 2 hours into a 4-hour cycle
        val now = LocalDateTime.of(2025, 1, 1, 12, 0, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(50, update.value)
    }

    @Test
    fun `next-day cycle - during active period should calculate correct value`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(22, 0)..LocalTime.of(2, 0),  // 10 PM to 2 AM
            values = 0..100,
            callback = {}
        )

        // At midnight, we're 2 hours into a 4-hour cycle
        val now = LocalDateTime.of(2025, 1, 2, 0, 0, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(50, update.value)
    }

    @Test
    fun `next-day cycle - outside cycle in afternoon should delay until evening start`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(22, 0)..LocalTime.of(2, 0),
            values = 0..100,
            callback = {},
            timeProvider = { LocalDateTime.of(2025, 1, 1, 15, 0, 0) }
        )

        val (update, delay) = timer.runCycle(LocalDateTime.of(2025, 1, 1, 15, 0, 0))

        assertNull(update)
        assertEquals(25_200_000L, delay) // 7 hours in millis
    }

    @Test
    fun `should handle custom step values correctly`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100 step 10,
            callback = {}
        )

        // At 10:24, we're 24 minutes (0.1 of cycle) into a 240-minute cycle
        // Should give us value 10 (first step after 0)
        val now = LocalDateTime.of(2025, 1, 1, 10, 24, 0)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(10, update.value)
    }

    @Test
    fun `should not return update when value hasn't changed`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        // First call at start
        val now1 = LocalDateTime.of(2025, 1, 1, 10, 0, 0)
        val (update1, _) = timer.runCycle(now1)
        assertNotNull(update1)
        assertEquals(0, update1.value)

        // Second call very shortly after (same value)
        val now2 = LocalDateTime.of(2025, 1, 1, 10, 0, 10)
        val (update2, _) = timer.runCycle(now2)
        assertNull(update2) // No update since value is still 0
    }

    @Test
    fun `should handle sub-second precision correctly`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(10, 1), // 1 min cycle
            values = 0..60,
            callback = {}
        )

        // At 30 seconds into the cycle
        val now = LocalDateTime.of(2025, 1, 1, 10, 0, 30)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(30, update.value)
    }

    @Test
    fun `next update time should be calculated correctly`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(11, 0), // 1 hr cycle
            values = 0..60, // Value changes every minute
            callback = {}
        )

        val now = LocalDateTime.of(2025, 1, 1, 10, 30, 0)
        val (update, delay) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(30, update.value)
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 31, 0), update.nextUpdateTime)
        assertEquals(60_000L, delay) // 1 minute to next value
    }

    @Test
    fun `should handle cycle boundary correctly when just after end time`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        // 5 seconds after end time - should still trigger end value update
        val now = LocalDateTime.of(2025, 1, 1, 14, 0, 5)
        val (update, _) = timer.runCycle(now)

        assertNotNull(update)
        assertEquals(100, update.value)

        // Second call should not trigger update (already at end value)
        val (update2, _) = timer.runCycle(LocalDateTime.of(2025, 1, 1, 14, 0, 7))
        assertNull(update2)
    }

    @Test
    fun `should handle cycle boundary correctly when more than 10 seconds after end`() {
        val timer = RampScheduler(
            timeBounds = LocalTime.of(10, 0)..LocalTime.of(14, 0),
            values = 0..100,
            callback = {}
        )

        // 15 seconds after end time - outside the 10-second window
        val now = LocalDateTime.of(2025, 1, 1, 14, 0, 15)
        val (update, delay) = timer.runCycle(now)

        assertNull(update)
        // Should delay until next day's start
        assertEquals(71_985_000L, delay) // ~20 hours until 10 AM next day
    }
}