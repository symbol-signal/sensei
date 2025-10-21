package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.OffsetDateTime.parse

data class SunTimes(
    val sunrise: OffsetDateTime,
    val sunset: OffsetDateTime,
)

class SunTimesService(private val client: HttpClient) {

    private val log = KotlinLogging.logger {}

    @Volatile
    private var _sunTimes: SunTimes? = null

    val sunTimes: SunTimes?
        get() = _sunTimes

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
        val resp = client.get("https://api.sunrise-sunset.org/json?lat=49.854&lng=18.54169&formatted=0&tzid=Europe/Prague")
        if (resp.status.value != 200) {
            log.warn { "unable_fetch_sun_times status=[${resp.status.value}]" }
        }
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<SunriseSunsetResponse>(resp.bodyAsText())
        _sunTimes = SunTimes(parse(parsed.results.sunrise), parse(parsed.results.sunset))
        log.info { "sun_times_updated new_times=[${sunTimes}]" }
    }

    fun isNight(): Boolean {
        val times = _sunTimes ?: return false
        val now = OffsetDateTime.now()
        return now.isAfter(times.sunset) || now.isBefore(times.sunrise)
    }
}
