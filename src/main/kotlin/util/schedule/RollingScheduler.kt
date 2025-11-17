package symsig.sensei.util.schedule

import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

typealias ScheduleProvider<V> = suspend () -> Collection<Pair<LocalDateTime, V>>

class RollingScheduler<V>(
    private val scheduleProvider: ScheduleProvider<V>,
    private val action: (V) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun run() {
        while (true) {
            val sortedSchedule = scheduleProvider().sortedBy { it.first }
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