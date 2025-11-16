package symsig.sensei

import symsig.sensei.services.SunTimesService
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MILLIS

fun durationInMinutes(start: LocalTime, end: LocalTime): Int {
    val minutes = ChronoUnit.MINUTES.between(start, end)
    return (if (minutes >= 0) minutes else minutes + 24 * 60).toInt()
}

data class TimeRange(val start: LocalTime, val end: LocalTime) {

    operator fun contains(time: LocalTime): Boolean {
        return if (start <= end) {
            time in start..<end  // Normal case: start=14:00, end=18:00
        } else {
            time !in end..<start  // Crosses midnight: start=23:00, end=02:00
        }
    }

    fun durationInMinutes() = durationInMinutes(start, end)

    fun interpolate(startValue: Double, endValue: Double): Double =
        interpolate(startValue, endValue, LocalTime.now())

    fun interpolate(startValue: Double, endValue: Double, current: LocalTime): Double {
        if (current !in this) {
            // Outside range - return start or end value based on which is closer
            return if (durationInMinutes(current, start) < durationInMinutes(end, current)) startValue else endValue
        }
        val totalMinutes = durationInMinutes()
        val elapsedMinutes = durationInMinutes(start, current)
        val progress = elapsedMinutes.toDouble() / totalMinutes
        return startValue + (endValue - startValue) * progress
    }
}

data class DateTimeRange(val start: LocalDateTime, val end: LocalDateTime) {
    val duration: Duration = Duration.between(start, end)

    fun <V> withProgression(values: Iterable<V>): List<Pair<LocalDateTime, V>> {
        val valueList = values.toList()

        if (valueList.isEmpty()) return emptyList()
        if (valueList.size == 1) return listOf(start to valueList.first())

        val stepIntervalMs = duration.toMillis() / (valueList.size - 1)
        return valueList.mapIndexed { index, value -> start.plus(stepIntervalMs * index, MILLIS) to value }
    }
}

enum class TimePeriod {
    DAYTIME,      // sunrise to sunset
    EVENING,      // sunset to bedtime
    WINDING_DOWN, // bedtime to sleep time
    NIGHTTIME     // sleep time to sunrise
}

class DayCycle(val sunTimesService: SunTimesService, val bedTime: LocalTime, val sleepTime: LocalTime) {
    private val todaySunTimes get() = sunTimesService.today

    val daytime get() = TimeRange(todaySunTimes.sunrise.toLocalTime(), todaySunTimes.sunset.toLocalTime())
    val evening get() = TimeRange(todaySunTimes.sunset.toLocalTime(), bedTime)
    val windingDown get() = TimeRange(bedTime, sleepTime)
    val nighttime get() = TimeRange(sleepTime, todaySunTimes.sunrise.toLocalTime())

    fun getCurrentPeriod(): TimePeriod {
        val now = LocalTime.now()
        return when (now) {
            in daytime -> TimePeriod.DAYTIME
            in evening -> TimePeriod.EVENING
            in windingDown -> TimePeriod.WINDING_DOWN
            else -> TimePeriod.NIGHTTIME
        }
    }

    fun getTimeRange(timePeriod: TimePeriod): TimeRange {
        return when (timePeriod) {
            TimePeriod.DAYTIME -> daytime
            TimePeriod.EVENING -> evening
            TimePeriod.WINDING_DOWN -> windingDown
            TimePeriod.NIGHTTIME -> nighttime
        }
    }

    fun interpolate(timePeriod: TimePeriod, startValue: Double, endValue: Double): Double {
        return getTimeRange(timePeriod).interpolate(startValue, endValue)
    }

    fun isDayTime() = LocalTime.now() in daytime
    fun isEvening() = LocalTime.now() in evening
    fun isWindingDown() = LocalTime.now() in windingDown
    fun isNightTime() = LocalTime.now() in nighttime
}
