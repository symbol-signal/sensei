package symsig.sensei.util.timer

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

    private var lastValue: Int? = null

    suspend fun run() {
        while (true) {
            val now = LocalDateTime.now()
            val cycleCalc = if (start.isBefore(end)) SameDayCycleCalc(start, end, now) else NextDayCycleCalc(start, end, now)

            if (cycleCalc.isOutsideCycle()) {
                if (Duration.between(end, now.toLocalTime()).abs() < Duration.ofSeconds(10) && lastValue != values.last) {
                    lastValue = values.last
                    callback(SequenceUpdate(values.last, now, cycleCalc.nextCycleStartAt().toLocalDateTime()))
                }
                delay(cycleCalc.durationUntilNewCycle().toMillis())
                continue
            }

            val elapsedDurationMs = Duration.between(start, now).toMillis()
            val cycleDurationMs = cycleCalc.cycleDuration().toMillis().toDouble()
            val range = kotlin.math.abs(values.last - values.first)
            val rangeValue = (elapsedDurationMs / cycleDurationMs) * range
            val newValue = (if (values.step > 0) (values.first + rangeValue) else (values.first - rangeValue)).roundToInt()

            val rateMs = cycleDurationMs / range
            val next = if (values.step > 0) (newValue - this.values.first + 1) * rateMs else (values.first - newValue + 1) * rateMs

            val nextRun: LocalTime = start.plus(next.toLong(), ChronoUnit.MILLIS)
            if (lastValue != newValue) {
                lastValue = newValue
                callback(SequenceUpdate(newValue, now, nextRun.atDate(now.toLocalDate())))
            }

            delay(Duration.between(now.toLocalTime(), nextRun).toMillis())
            continue
        }
    }
}

private abstract class CycleCalc(val start: LocalTime, val end: LocalTime, val now: LocalDateTime) {

    abstract fun isOutsideCycle(): Boolean

    abstract fun daysUntilNewCycle(): Int

    abstract fun cycleDuration(): Duration

    fun nextCycleStartAt(): ZonedDateTime = ZonedDateTime.of(now.toLocalDate().plusDays(daysUntilNewCycle().toLong()), start, systemDefault())

    fun durationUntilNewCycle(): Duration = Duration.between(now.atZone(systemDefault()), nextCycleStartAt())
}

private class SameDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start) || (now.toLocalTime() == end || now.toLocalTime().isAfter(end))

    override fun daysUntilNewCycle() = if (now.toLocalTime().isBefore(start)) 0 else 1

    override fun cycleDuration(): Duration = Duration.between(start, end)
}

private class NextDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start) && (now.toLocalTime() == end || now.toLocalTime().isAfter(end))

    override fun daysUntilNewCycle() = 0

    override fun cycleDuration(): Duration =
        Duration.between(start.atDate(now.toLocalDate()), end.atDate(now.toLocalDate().plusDays(1)))
}

fun main() {
//    val phase = LinearSequenceTimer(LocalTime.of(12, 48)..LocalTime.of(12, 50), 200..400) { e -> println(e) }
    val phase = LinearSequenceTimer(LocalTime.of(13, 4)..LocalTime.of(13, 5), 400 downTo 200) { e -> println(e) }
    runBlocking {
        phase.run()
    }
}
