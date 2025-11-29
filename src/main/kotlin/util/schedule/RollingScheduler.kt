package symsig.sensei.util.schedule

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

/**
 * Executes scheduled actions at specific times, re-fetching the schedule after each cycle.
 *
 * The scheduler runs continuously, executing [action] for each scheduled time.
 * When a schedule completes, it fetches a fresh one - useful for schedules based on
 * dynamic values like sunset times.
 *
 * If multiple entries share the same timestamp, only the last one is kept (Last-value-wins per timestamp rule).
 *
 * @param schedule Provides time/value pairs for each cycle
 * @param action Called with the value at each scheduled time
 */
class RollingScheduler<V>(
    private val schedule: suspend () -> Collection<Pair<LocalDateTime, V>>,
    private val action: suspend (V) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    /**
     * Starts the scheduler. Runs indefinitely until cancelled.
     */
    suspend fun run() {
        while (true) {
            val sortedSchedule = schedule()
                .associateBy { it.first }
                .values
                .sortedBy { it.first }
            if (sortedSchedule.isEmpty()) {
                log.debug { "empty_schedule_returned action=[wait_and_retry]" }
                delay(10_000)
                continue
            }
            runForSchedule(sortedSchedule)
        }
    }

    private tailrec suspend fun runForSchedule(schedule: List<Pair<LocalDateTime, V>>) {
        val now = LocalDateTime.now(clock)
        val (past, future) = schedule.partition { it.first <= now }

        past.lastOrNull()?.let { action(it.second) }

        val next = future.firstOrNull()
        if (next != null) {
            delay(Duration.between(now, next.first).toMillis().coerceAtLeast(0))
            runForSchedule(future)
        }
    }
}