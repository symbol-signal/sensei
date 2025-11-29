package symsig.sensei

import java.time.LocalDate
import java.time.LocalTime.of
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeParsingTest {
    private val date = LocalDate.of(2024, 1, 15)

    @Test
    fun `parses am times`() {
        assertEquals(of(3, 0), time("3am").forDate(date).toLocalTime())
        assertEquals(of(3, 0), time("3AM").forDate(date).toLocalTime())
        assertEquals(of(11, 0), time("11am").forDate(date).toLocalTime())
    }

    @Test
    fun `parses pm times`() {
        assertEquals(of(15, 0), time("3pm").forDate(date).toLocalTime())
        assertEquals(of(15, 0), time("3PM").forDate(date).toLocalTime())
        assertEquals(of(23, 0), time("11pm").forDate(date).toLocalTime())
    }

    @Test
    fun `parses 12am as midnight`() {
        assertEquals(of(0, 0), time("12am").forDate(date).toLocalTime())
    }

    @Test
    fun `parses 12pm as noon`() {
        assertEquals(of(12, 0), time("12pm").forDate(date).toLocalTime())
    }

    @Test
    fun `parses am pm times with minutes`() {
        assertEquals(of(3, 30), time("3:30am").forDate(date).toLocalTime())
        assertEquals(of(15, 45), time("3:45pm").forDate(date).toLocalTime())
    }

    @Test
    fun `parses 24-hour format`() {
        assertEquals(of(14, 0), time("14:00").forDate(date).toLocalTime())
        assertEquals(of(0, 30), time("00:30").forDate(date).toLocalTime())
        assertEquals(of(23, 59), time("23:59").forDate(date).toLocalTime())
    }

    @Test
    fun `handles whitespace`() {
        assertEquals(of(3, 0), time(" 3am ").forDate(date).toLocalTime())
        assertEquals(of(14, 0), time(" 14:00 ").forDate(date).toLocalTime())
    }
}
