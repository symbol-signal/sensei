package symsig.sensei

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit.MILLIS
import java.util.Locale
import kotlin.time.toJavaDuration

data class DateTimeRange(val start: LocalDateTime, val end: LocalDateTime) {
    val duration: Duration = Duration.between(start, end)

    operator fun contains(dateTime: LocalDateTime): Boolean = dateTime in start..<end

    fun <V> spread(values: Iterable<V>): List<Pair<LocalDateTime, V>> {
        val valueList = values.toList()

        if (valueList.isEmpty()) return emptyList()
        if (valueList.size == 1) return listOf(start to valueList.first())

        val stepIntervalMs = duration.toMillis() / (valueList.size - 1)
        return valueList.mapIndexed { index, value -> start.plus(stepIntervalMs * index, MILLIS) to value }
    }

    /**
     * Interpolate a value based on position within this range.
     * Returns startValue at range start, endValue at range end, linearly interpolated between.
     * If dateTime is outside the range, returns the nearest boundary value.
     */
    fun interpolate(dateTime: LocalDateTime, startValue: Double, endValue: Double): Double {
        if (dateTime <= start) return startValue
        if (dateTime >= end) return endValue

        val elapsed = Duration.between(start, dateTime).toMillis()
        val progress = elapsed.toDouble() / duration.toMillis()
        return startValue + (endValue - startValue) * progress
    }
}

/**
 * A token representing a point in time that can be resolved for any date.
 */
interface TimeToken {
    fun forDate(date: LocalDate): LocalDateTime

    operator fun plus(d: kotlin.time.Duration): TimeToken = OffsetToken(this, d)
    operator fun minus(d: kotlin.time.Duration): TimeToken = OffsetToken(this, -d)
}

/**
 * A fixed time of day (e.g., 2:00 AM).
 */
class FixedTimeToken(private val time: LocalTime) : TimeToken {
    override fun forDate(date: LocalDate): LocalDateTime = date.atTime(time)
}

/**
 * A token with an offset applied (e.g., SUNSET + 1.hour).
 */
class OffsetToken(private val base: TimeToken, private val offset: kotlin.time.Duration) : TimeToken {
    override fun forDate(date: LocalDate): LocalDateTime =
        base.forDate(date).plus(offset.toJavaDuration())

    override fun plus(d: kotlin.time.Duration): TimeToken = OffsetToken(base, offset + d)
    override fun minus(d: kotlin.time.Duration): TimeToken = OffsetToken(base, offset - d)
}

private val timeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
    .optionalStart().appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2).optionalEnd()
    .appendText(ChronoField.AMPM_OF_DAY)
    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
    .toFormatter(Locale.ENGLISH)

/**
 * Helper to create a fixed time token from a string like "2am", "14:30", etc.
 */
fun time(s: String): TimeToken {
    val normalized = s.trim()
    val localTime = try {
        LocalTime.parse(normalized, timeFormatter)
    } catch (_: Exception) {
        LocalTime.parse(normalized)
    }
    return FixedTimeToken(localTime)
}

/**
 * An abstract time window defined by start and end tokens.
 */
class Window(val start: TimeToken, val end: TimeToken) {

    /**
     * Resolve this window for a specific date.
     * Handles cross-midnight by pushing end to next day if needed.
     */
    private fun resolveForDate(date: LocalDate): DateTimeRange {
        val startDT = start.forDate(date)
        var endDT = end.forDate(date)

        if (endDT <= startDT) {
            endDT = end.forDate(date.plusDays(1))
        }

        return DateTimeRange(startDT, endDT)
    }

    /**
     * Resolve this window to a concrete DateTimeRange relative to the given moment.
     * Returns the range that contains `now`, or the next upcoming range.
     */
    fun resolve(now: LocalDateTime): DateTimeRange {
        val today = now.toLocalDate()

        // Try yesterday (for cases like 1am checking window(sunset, 2am))
        val yesterdayRange = resolveForDate(today.minusDays(1))
        if (now in yesterdayRange) return yesterdayRange

        // Try today
        val todayRange = resolveForDate(today)
        if (now in todayRange || now < todayRange.start) return todayRange

        // Past today's range - use tomorrow
        return resolveForDate(today.plusDays(1))
    }

    /**
     * Check if the given moment falls within this window.
     */
    operator fun contains(now: LocalDateTime): Boolean {
        val range = resolve(now)
        return now in range
    }

    /**
     * Interpolate a value based on position within this window.
     */
    fun interpolate(now: LocalDateTime, startValue: Double, endValue: Double): Double =
        resolve(now).interpolate(now, startValue, endValue)

    /**
     * Spread values evenly across this window.
     */
    fun <V> spread(now: LocalDateTime, values: Iterable<V>): List<Pair<LocalDateTime, V>> =
        resolve(now).spread(values)
}

/**
 * Create a window from two time tokens.
 */
fun window(start: TimeToken, end: TimeToken) = Window(start, end)
fun window(start: TimeToken, end: String) = Window(start, time(end))
fun window(start: String, end: TimeToken) = Window(time(start), end)
fun window(start: String, end: String) = Window(time(start), time(end))