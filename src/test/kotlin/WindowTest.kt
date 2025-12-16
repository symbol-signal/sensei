package symsig.sensei

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowTest {

    private val daytimeWindow = window(time("9am"), time("9pm"))
    private val nighttimeWindow = window(time("9pm"), time("9am"))

    @Test
    fun `windows resolve correctly at noon`() {
        val noon = LocalDateTime.of(2024, 1, 15, 12, 0)

        val daytime = daytimeWindow.resolve(noon)
        assertEquals(LocalDateTime.of(2024, 1, 15, 9, 0), daytime.start)
        assertEquals(LocalDateTime.of(2024, 1, 15, 21, 0), daytime.end)

        val nighttime = nighttimeWindow.resolve(noon)
        assertEquals(LocalDateTime.of(2024, 1, 15, 21, 0), nighttime.start)
        assertEquals(LocalDateTime.of(2024, 1, 16, 9, 0), nighttime.end)
    }
}
