package symsig.sensei.util.schedule

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

class RollingScheduler<V>(
    private val schedule: suspend () -> Collection<Pair<LocalDateTime, V>>,
    private val action: suspend (V) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun run() {
        while (true) {
            val sortedSchedule = schedule().sortedBy { it.first }
            if (sortedSchedule.isEmpty()) {
                log.debug { "empty_schedule_returned action=[wait_and_retry]" }
                delay(10_000)
                continue
            }
            val (past, future) = sortedSchedule.partition { it.first <= LocalDateTime.now(clock) }
            val trimmedSchedule = listOfNotNull(past.lastOrNull()) + future
            runForSchedule(trimmedSchedule)
        }
    }

    private tailrec suspend fun runForSchedule(schedule: Collection<Pair<LocalDateTime, V>>) {
        val now = LocalDateTime.now(clock)
        val (pastSchedule, futureSchedule) = schedule.partition { it.first <= now }

        pastSchedule.forEach { action(it.second) }
        val next = futureSchedule.firstOrNull()
        if (next != null) {
            delay(Duration.between(now, next.first).toMillis())
            runForSchedule(futureSchedule)
        }
    }
}