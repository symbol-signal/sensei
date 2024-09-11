package symsig.sensei

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId.systemDefault
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class SequenceUpdate(
    val value: Int,
    val updateTime: LocalDateTime,
    val nextUpdateTime: LocalDateTime,
)


class LinearSequenceTimer(
    val timeBounds: ClosedRange<LocalTime>,
    val values: IntProgression,
    private val callback: (SequenceUpdate) -> Unit
) {

    private val start = timeBounds.start
    private val end = timeBounds.endInclusive
    private val intervalDuration = Duration.between(start, end)

    private var lastValue: Int? = null

    suspend fun run() {
        while (true) {
            val nowDateTime = LocalDateTime.now()
            val cycleCalc = if (start.isBefore(end)) SameDayCycleCalc(start, end, nowDateTime) else NextDayCycleCalc(
                start,
                end,
                nowDateTime
            )

            if (cycleCalc.isOutsideCycle()) {
                delay(cycleCalc.durationUntilNewCycle().toMillis())
                continue
            }

            val durationMs = cycleCalc.cycleDuration().toMillis().toDouble()
            val range = values.last - values.first
            val currentTime = Duration.between(start, nowDateTime).toMillis()
            val newValue = ((currentTime / durationMs) * range + values.first).roundToInt()


            val rateMs = durationMs / range
            val next = (newValue - this.values.first + 1) * rateMs

            val nextRun: LocalTime = start.plus(next.toLong(), ChronoUnit.MILLIS)
            if (lastValue != newValue) {
                lastValue = newValue
                callback(SequenceUpdate(newValue, nowDateTime, nextRun.atDate(nowDateTime.toLocalDate())))
            }

            delay(Duration.between(nowDateTime.toLocalTime(), nextRun).toMillis())
            continue
        }

    }
}

private abstract class CycleCalc(val start: LocalTime, val end: LocalTime, val now: LocalDateTime) {

    abstract fun isOutsideCycle(): Boolean

    abstract fun daysUntilNewCycle(): Int

    abstract fun cycleDuration(): Duration

    fun durationUntilNewCycle(): Duration {
        val plusDays: Long = daysUntilNewCycle().toLong()
        val nextCycleStart = ZonedDateTime.of(now.toLocalDate().plusDays(plusDays), start, systemDefault())
        return Duration.between(now.atZone(systemDefault()), nextCycleStart)
    }
}

private class SameDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start) || now.toLocalTime().isAfter(end)

    override fun daysUntilNewCycle() = if (now.toLocalTime().isBefore(start)) 0 else 1

    override fun cycleDuration(): Duration = Duration.between(start, end)
}

private class NextDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start) && now.toLocalTime().isAfter(end)

    override fun daysUntilNewCycle() = 0

    override fun cycleDuration(): Duration =
        Duration.between(start.atDate(now.toLocalDate()), end.atDate(now.toLocalDate().plusDays(1)))
}

fun main() {
    val phase = LinearSequenceTimer(LocalTime.of(12, 40)..LocalTime.of(12, 48), 100..200) { e -> println(e) }
    runBlocking {
        phase.run()
    }
}
