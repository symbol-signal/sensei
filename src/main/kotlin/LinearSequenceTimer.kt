package symsig.sensei

import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId.systemDefault
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class SequenceUpdate(val value: Int, val updateTime: LocalDateTime, val nextUpdateTime: LocalDateTime)


class LinearSequenceTimer(
    val timeBounds: ClosedRange<LocalTime>,
    val values: IntProgression,
    private val callback: suspend (SequenceUpdate) -> Unit,
    private val timeProvider: () -> LocalDateTime = { LocalDateTime.now() }
) {

    private val start = timeBounds.start
    private val end = timeBounds.endInclusive

    private var lastValue: Int? = null

    init {
        require(start != end) {
            "Time bounds must be distinctive values"
        }
    }

    suspend fun run() {
        while (true) {
            val (update, delay) = runCycle(timeProvider())
            update?.let { callback(update) }
            delay(delay)
        }
    }

    fun runCycle(now: LocalDateTime): Pair<SequenceUpdate?, Long> {
        val cycleCalc =
            if (start.isBefore(end)) SameDayCycleCalc(start, end, now) else NextDayCycleCalc(start, end, now)

        if (cycleCalc.isOutsideCycle()) {
            var update: SequenceUpdate? = null
            if (Duration.between(end, now.toLocalTime())
                    .abs() < Duration.ofSeconds(10) && lastValue != values.last
            ) {
                lastValue = values.last
                update = SequenceUpdate(values.last, now, cycleCalc.nextCycleStartAt().toLocalDateTime())
            }
            return Pair(update, cycleCalc.durationUntilNewCycle().toMillis())
        }

        val elapsedDurationMs = Duration.between(cycleCalc.cycleStartDateTime(), now).toMillis()
        val cycleDurationMs = cycleCalc.cycleDuration().toMillis().toDouble()
        val range = kotlin.math.abs(values.last - values.first)
        val rangeValue = (elapsedDurationMs / cycleDurationMs) * range
        val newValue =
            (if (values.step > 0) (values.first + rangeValue) else (values.first - rangeValue)).roundToInt()

        val rateMs = cycleDurationMs / range
        val next =
            if (values.step > 0) (newValue - this.values.first + 1) * rateMs else (values.first - newValue + 1) * rateMs

        val nextRun: LocalTime = start.plus(next.toLong(), ChronoUnit.MILLIS)
        var update: SequenceUpdate? = null
        if (lastValue != newValue) {
            lastValue = newValue
            update = SequenceUpdate(newValue, now, nextRun.atDate(now.toLocalDate()))
        }

        return Pair(update, Duration.between(now.toLocalTime(), nextRun).toMillis())
    }
}

private abstract class CycleCalc(val start: LocalTime, val end: LocalTime, val now: LocalDateTime) {

    abstract fun isOutsideCycle(): Boolean

    abstract fun daysUntilNewCycle(): Int

    abstract fun cycleDuration(): Duration

    /**
     * Gets the actual start date and time of the *current* cycle.
     * This is needed to handle overnight cycles correctly.
     */
    abstract fun cycleStartDateTime(): LocalDateTime

    fun nextCycleStartAt(): ZonedDateTime =
        ZonedDateTime.of(now.toLocalDate().plusDays(daysUntilNewCycle().toLong()), start, systemDefault())

    fun durationUntilNewCycle(): Duration = Duration.between(now.atZone(systemDefault()), nextCycleStartAt())
}

private class SameDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() =
        now.toLocalTime().isBefore(start) || (now.toLocalTime() == end || now.toLocalTime().isAfter(end))

    override fun daysUntilNewCycle() = if (now.toLocalTime().isBefore(start)) 0 else 1

    override fun cycleDuration(): Duration = Duration.between(start, end)

    override fun cycleStartDateTime(): LocalDateTime = start.atDate(now.toLocalDate())
}

private class NextDayCycleCalc(start: LocalTime, end: LocalTime, now: LocalDateTime) : CycleCalc(start, end, now) {

    override fun isOutsideCycle() =
        now.toLocalTime().isBefore(start) && (now.toLocalTime() == end || now.toLocalTime().isAfter(end))

    override fun daysUntilNewCycle() = 0

    override fun cycleDuration(): Duration =
        Duration.between(start.atDate(now.toLocalDate()), end.atDate(now.toLocalDate().plusDays(1)))

    override fun cycleStartDateTime(): LocalDateTime {
        // If 'now' is after midnight but before the 'end' time, the cycle must have started on the previous day.
        val startDate = if (now.toLocalTime().isBefore(end)) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate() // Otherwise, the cycle started today (e.g., at 23:00)
        }
        return start.atDate(startDate)
    }
}