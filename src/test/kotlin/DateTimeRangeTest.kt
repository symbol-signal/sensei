package symsig.sensei

import java.time.LocalDateTime.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTimeRangeTest {

    private val start = of(2024, 1, 1, 10, 0)
    private val end = of(2024, 1, 1, 12, 0)
    private val sut = DateTimeRange(start, end)

    @Test
    fun `contains returns true for datetime within range`() {
        assertTrue(of(2024, 1, 1, 11, 0) in sut)
    }

    @Test
    fun `contains returns true for datetime at start`() {
        assertTrue(start in sut)
    }

    @Test
    fun `contains returns false for datetime at end`() {
        assertFalse(end in sut)
    }

    @Test
    fun `contains returns false for datetime before range`() {
        assertFalse(of(2024, 1, 1, 9, 0) in sut)
    }

    @Test
    fun `contains returns false for datetime after range`() {
        assertFalse(of(2024, 1, 1, 13, 0) in sut)
    }

    @Test
    fun `spread with empty list returns empty`() {
        assertEquals(emptyList(), sut.spread(emptyList<Int>()))
    }

    @Test
    fun `spread with single value returns start time`() {
        val result = sut.spread(listOf(42))
        assertEquals(listOf(start to 42), result)
    }

    /**
     * Range: 10:00 to 12:00 (2 hours)
     * Values: [1, 2, 3]
     * Step interval: 2 hours / (3-1) = 1 hour
     *
     * Result:
     *   10:00 → 1 (start)
     *   11:00 → 2 (start + 1 hour)
     *   12:00 → 3 (start + 2 hours = end)
     */
    @Test
    fun `spread distributes values evenly`() {
        val result = sut.spread(listOf(1, 2, 3))
        assertEquals(3, result.size)
        assertEquals(start to 1, result[0])
        assertEquals(of(2024, 1, 1, 11, 0) to 2, result[1])
        assertEquals(end to 3, result[2])
    }

    @Test
    fun `interpolate returns startValue at range start`() {
        assertEquals(100.0, sut.interpolate(start, 100.0, 0.0))
    }

    @Test
    fun `interpolate returns endValue at range end`() {
        assertEquals(0.0, sut.interpolate(end, 100.0, 0.0))
    }

    @Test
    fun `interpolate returns midpoint at range middle`() {
        val middle = of(2024, 1, 1, 11, 0)
        assertEquals(50.0, sut.interpolate(middle, 100.0, 0.0))
    }

    @Test
    fun `interpolate clamps to startValue before range`() {
        val before = of(2024, 1, 1, 9, 0)
        assertEquals(100.0, sut.interpolate(before, 100.0, 0.0))
    }

    @Test
    fun `interpolate clamps to endValue after range`() {
        val after = of(2024, 1, 1, 13, 0)
        assertEquals(0.0, sut.interpolate(after, 100.0, 0.0))
    }
}
