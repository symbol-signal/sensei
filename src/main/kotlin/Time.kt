package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.OffsetDateTime.parse
import java.time.temporal.ChronoUnit

data class SunTimes(val sunrise: LocalTime, val sunset: LocalTime)

class SunTimesService(private val client: HttpClient) {

    private val log = KotlinLogging.logger {}

    @Volatile
    private var _sunTimes: SunTimes = SunTimes(LocalTime.of(6, 30), LocalTime.of(19, 30))

    val sunTimes: SunTimes
        get() = _sunTimes

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SunriseSunsetResponse(
        val results: SunriseSunsetResults,
        val status: String,
        val tzid: String
    )

    @Serializable
    data class SunriseSunsetResults(
        val sunrise: String,
        val sunset: String,
    )

    suspend fun updateTimes() {
        val resp =
            client.get("https://api.sunrise-sunset.org/json?lat=49.854&lng=18.54169&formatted=0&tzid=Europe/Prague")
        if (resp.status.value != 200) {
            log.warn { "unable_fetch_sun_times status=[${resp.status.value}]" }
            return
        }
        val respObj = json.decodeFromString<SunriseSunsetResponse>(resp.bodyAsText())
        _sunTimes = SunTimes(
            sunrise = parse(respObj.results.sunrise).toLocalTime(),
            sunset = parse(respObj.results.sunset).toLocalTime()
        )
        log.info { "sun_times_updated new_times=[${sunTimes}]" }
    }
}

enum class TimePeriod {
    DAYTIME,      // sunrise to sunset
    EVENING,      // sunset to bedtime
    WINDING_DOWN, // bedtime to sleep time
    NIGHTTIME     // sleep time to sunrise
}

class DayCycle(val sunTimesService: SunTimesService, val bedTime: LocalTime, val sleepTime: LocalTime) {

    fun getCurrentPeriod(): TimePeriod {
        val now = LocalTime.now()
        val times = sunTimesService.sunTimes

        return when {
            isTimeBetween(now, times.sunrise, times.sunset) -> TimePeriod.DAYTIME
            isTimeBetween(now, times.sunset, bedTime) -> TimePeriod.EVENING
            isTimeBetween(now, bedTime, sleepTime) -> TimePeriod.WINDING_DOWN
            else -> TimePeriod.NIGHTTIME
        }
    }

    fun getTimeRange(timePeriod: TimePeriod): Pair<LocalTime, LocalTime> {
        val times = sunTimesService.sunTimes
        return when (timePeriod) {
            TimePeriod.DAYTIME -> times.sunrise to times.sunset
            TimePeriod.EVENING -> times.sunset to bedTime
            TimePeriod.WINDING_DOWN -> bedTime to sleepTime
            TimePeriod.NIGHTTIME -> sleepTime to times.sunrise
        }
    }

    fun interpolate(timePeriod: TimePeriod, startValue: Double, endValue: Double): Double {
        val (start, end) = getTimeRange(timePeriod)
        return interpolate(start, end, startValue, endValue)
    }

    fun isDayTime() = getCurrentPeriod() == TimePeriod.DAYTIME
    fun isEvening() = getCurrentPeriod() == TimePeriod.EVENING
    fun isWindingDown() = getCurrentPeriod() == TimePeriod.WINDING_DOWN
    fun isNightTime() = getCurrentPeriod() == TimePeriod.NIGHTTIME
}

fun isTimeBetween(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
    return if (start <= end) {
        time in start..<end  // Normal case: start=14:00, end=18:00
    } else {
        time !in end..<start  // Crosses midnight: start=23:00, end=02:00
    }
}

fun durationInMinutes(start: LocalTime, end: LocalTime): Int {
    val minutes = ChronoUnit.MINUTES.between(start, end)
    return (if (minutes >= 0) minutes else minutes + 24 * 60).toInt()
}

/**
 * Interpolate a value based on current position within a time range
 * Example: interpolate(now, sunset, bedtime, 100.0, 30.0) for dimming lights
 * TODO Write tests
 */
fun interpolate(start: LocalTime, end: LocalTime, startValue: Double, endValue: Double): Double =
    interpolate(LocalTime.now(), start, end, startValue, endValue)

fun interpolate(current: LocalTime, start: LocalTime, end: LocalTime, startValue: Double, endValue: Double): Double {
    if (!isTimeBetween(current, start, end)) {
        // Outside range - return start or end value based on which is closer
        return if (durationInMinutes(current, start) < durationInMinutes(end, current)) startValue else endValue
    }

    val totalMinutes = durationInMinutes(start, end)
    val elapsedMinutes = durationInMinutes(start, current)
    val progress = elapsedMinutes.toDouble() / totalMinutes

    return startValue + (endValue - startValue) * progress
}