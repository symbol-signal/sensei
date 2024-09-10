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


class TimedLinearSequenceNotifier(
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
            val nowTime = nowDateTime.toLocalTime()
            val state = if (start.isBefore(end)) SameDayIntervalStrategy(start, end, nowDateTime) else NextDayIntervalStrategy(start, end, nowDateTime)

            if (state.isOutsideCycle()) {
                delay(state.durationUntilNewCycle().toMillis())
                continue
            }

            val durationMs = state.cycleDuration().toMillis().toDouble()
            val range = values.last - values.first
            val currentTime = Duration.between(start, nowDateTime).toMillis()
            val newValue = (currentTime / durationMs * range).roundToInt()
            if (lastValue != newValue) {
                lastValue = newValue
            }
            val rateMs = durationMs / range
            val next = (newValue - this.values.first + 1) * rateMs

            val nextRun: LocalTime = start.plus(next.toLong(), ChronoUnit.MILLIS)
            callback(SequenceUpdate(lastValue!!, nowDateTime, nextRun.atDate(nowDateTime.toLocalDate())))

            delay(Duration.between(nowDateTime.toLocalTime(), nextRun).toMillis())
            continue
        }

    }
}

private abstract class IntervalStrategy(val start: LocalTime, val end: LocalTime, val now: LocalDateTime) {

    abstract fun isOutsideCycle(): Boolean

    abstract fun daysUntilNewCycle(): Int

    abstract fun cycleDuration(): Duration

    fun durationUntilNewCycle(): Duration {
        val plusDays: Long = daysUntilNewCycle().toLong()
        val nextCycleStart = ZonedDateTime.of(now.toLocalDate().plusDays(plusDays), start, systemDefault())
        return Duration.between(now.atZone(systemDefault()), nextCycleStart)
    }
}

private class SameDayIntervalStrategy(start: LocalTime, end: LocalTime, now: LocalDateTime) : IntervalStrategy(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start)

    override fun daysUntilNewCycle() = if (now.toLocalTime().isBefore(start)) 0 else 1

    override fun cycleDuration(): Duration = Duration.between(start, end)
}

private class NextDayIntervalStrategy(start: LocalTime, end: LocalTime, now: LocalDateTime) : IntervalStrategy(start, end, now) {

    override fun isOutsideCycle() = now.toLocalTime().isBefore(start) && now.toLocalTime().isAfter(end)

    override fun daysUntilNewCycle() = 0

    override fun cycleDuration(): Duration = Duration.between(start.atDate(now.toLocalDate()), end.atDate(now.toLocalDate().plusDays(1)))
}

fun main() {
    val phase = TimedLinearSequenceNotifier(LocalTime.of(12, 25)..LocalTime.of(12, 26), 0..200) { e -> println(e) }
    runBlocking {
        phase.run()
    }
}
