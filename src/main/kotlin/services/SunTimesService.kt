package symsig.sensei.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import symsig.sensei.TimeRange
import java.io.IOException
import java.lang.Exception
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

data class SunTimes(val date: LocalDate, val sunrise: LocalDateTime, val sunset: LocalDateTime) {

    val range = TimeRange(sunrise.toLocalTime(), sunset.toLocalTime())
}

class SunTimesFetchException(message: String, cause: Throwable? = null) : IOException(message, cause)

class SunTimesService(private val client: HttpClient) {

    private val log = KotlinLogging.logger {}

    private val json = Json { ignoreUnknownKeys = true }

    private val cachedTimes: ConcurrentMap<LocalDate, SunTimes> = ConcurrentHashMap()

    private fun getTimesForDate(date: LocalDate): SunTimes {
        return cachedTimes[date] ?: getDefaultTimes(date)
    }

    val today: SunTimes
        get() = getTimesForDate(LocalDate.now())

    val tomorrow: SunTimes
        get() = getTimesForDate(LocalDate.now().plusDays(1))

    /**
     * Returns the currently relevant sun times.
     * - If we're before today's sunset: returns today's times
     * - If we're after today's sunset: returns tomorrow's times
     *
     * This ensures you always get the "active" or upcoming sun cycle.
     */
    val current: SunTimes
        get() {
            val now = LocalDateTime.now()
            val today = LocalDate.now()
            val todayTimes = getTimesForDate(today)
            return if (now.isBefore(todayTimes.sunset)) {
                todayTimes
            } else {
                getTimesForDate(today.plusDays(1))
            }
        }

    fun dayTime(): TimeRange = current.range

    fun fromSunsetFor(duration: kotlin.time.Duration) {
        val now = LocalDateTime.now()
    }

    private fun getDefaultTimes(day: LocalDate): SunTimes {
        return SunTimes(day, day.atTime(6, 30), day.atTime(19, 30))
    }

    suspend fun fetchTimes(day: LocalDate): SunTimes {
        val zone = ZoneId.systemDefault().id
        val resp =
            client.get("https://api.sunrise-sunset.org/json?lat=49.854&lng=18.54169&formatted=0&tzid=$zone&date=${day}")
        if (resp.status.value != 200) {
            throw SunTimesFetchException("Failed to fetch sun times: HTTP ${resp.status.value}")
        }
        val respObj = json.decodeFromString<SunriseSunsetResponse>(resp.bodyAsText())
        return SunTimes(
            date = day,
            sunrise = LocalDateTime.parse(respObj.results.sunrise),
            sunset = LocalDateTime.parse(respObj.results.sunset)
        )
    }

    private suspend fun fetchAndCache(day: LocalDate) {
        cachedTimes[day] = fetchTimes(day)
    }

    suspend fun update() {
        val today = LocalDate.now()

        fetchAndCache(today)
        fetchAndCache(today.plusDays(1))
        fetchAndCache(today.plusDays(2))

        cleanupCache(today)

        log.info { "sun_times_updated today=[$today] tomorrow=[$tomorrow]" }
    }

    suspend fun runUpdate() {
        var consecutiveFailures = 0
        while (true) {
            try {
                update()
            } catch (e: Exception) {
                consecutiveFailures++
                val retryDelay =
                    minOf(5 * 60 * 1000L * consecutiveFailures, 60 * 60 * 1000L) // 5 min * failures, max 1 hour
                log.warn { "sun_times_update_failed error=[${e.message}] retry_in_ms=[$retryDelay]" }
                delay(retryDelay)
                continue
            }
            consecutiveFailures = 0
            val todayDate = today.date
            val startOfTomorrow = todayDate.plusDays(1).atStartOfDay()
            val delayUntilTomorrow = Duration.between(LocalDateTime.now(), startOfTomorrow)

            delay(delayUntilTomorrow.toMillis().coerceAtLeast(1000))
        }
    }

    private fun cleanupCache(today: LocalDate) {
        val maxDate = today.plusDays(30)

        cachedTimes.keys.removeIf { date ->
            date.isBefore(today) || date.isAfter(maxDate)
        }
    }

    @Serializable
    data class SunriseSunsetResponse(val results: SunriseSunsetResults, val status: String, val tzid: String)

    @Serializable
    data class SunriseSunsetResults(val sunrise: String, val sunset: String)
}