package symsig.sensei.util.timer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val log = KotlinLogging.logger {}

class DailyTimer(val at: LocalTime, private val callback: suspend () -> Unit) {

    suspend fun run() {
        while (true) {
            delay(calculateNextTargetDelay().toMillis())

            try {
                callback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "[timer_callback_error]" }
            }
        }
    }

    private fun calculateNextTargetDelay(): Duration {
        val localZone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(localZone)
        var target = ZonedDateTime.of(now.toLocalDate(), at, localZone)
        if (now.isAfter(target) || now == target) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target)
    }
}
