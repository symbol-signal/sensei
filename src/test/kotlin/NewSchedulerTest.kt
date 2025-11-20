package symsig.sensei

import java.time.Duration
import java.time.LocalDateTime.of
import java.time.LocalTime
import java.time.temporal.ChronoUnit.MILLIS
import kotlin.math.roundToLong
import kotlin.test.Test

class NewSchedulerTest {

    // Usage
    @Test
    fun test0() {
        val timeRange = DateTimeRange(
            start = of(2025, 11, 5, 10, 0),
            end = of(2025, 11, 5, 11, 0)
        )

        // Different progressions over the same time range
        val brightening = timeRange.withProgression(0..100 step 10)
        val dimming = timeRange.withProgression(100 downTo 0 step 5)
        val temperatures = timeRange.withProgression(listOf(2700, 3000, 3500, 4000, 5000))

        brightening.forEach { (time, value) ->
            println("$time -> $value%")
        }
    }

    @Test
    fun test1() {
        val startDT = of(2025, 11, 5, 10, 0)
        val endDT = of(2025, 11, 5, 11, 0)
        val betweenMs = Duration.between(startDT, endDT).toMillis()
        val values = 10..21
        val stepCount = values.count()
        println(stepCount)
        val stepInterval = betweenMs / (stepCount - 1)
        println(stepInterval)

        for ((index, value) in values.withIndex()) {
            println("Step $index: value = $value")
            println(startDT.plus(stepInterval * index, MILLIS))
        }
    }

    @Test
    fun testLocalTimeRamp() {
        val startT = LocalTime.of(10, 0)
        val endT = LocalTime.of(11, 0)
        val betweenMs = Duration.between(startT, endT).toMillis().toDouble()
        val values = 10..20
        val stepCount = values.count()
        val stepInterval = betweenMs / (stepCount - 1)

        println("Step count: $stepCount")
        println("Step interval (ms): $stepInterval")

        for ((index, value) in values.withIndex()) {
            val millisOffset = (stepInterval * index).roundToLong()
            val time = startT.plus(millisOffset, MILLIS)
            println("Step $index: value = $value at $time")
        }
    }
}
