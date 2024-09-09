package symsig.sensei

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class TimedLinearSequence(val start: LocalTime, val end: LocalTime, val from: Int, val to: Int) {

    private var lastValue = from

    suspend fun run() {
        while (true) {
            val now = LocalDateTime.now()

            if (now.toLocalTime().isBefore(start)) {
                delay(Duration.between(now, LocalDateTime.of(now.toLocalDate(), start)).toMillis())
                continue
            }

            if (now.toLocalTime().isAfter(end)) {
                if (lastValue != to) {
                    lastValue = to
                }
                println(lastValue)
                val nextRun = LocalDateTime.of(now.toLocalDate().plusDays(1), start)
                delay(Duration.between(now, nextRun).toMillis())
                continue
            }

            val durationMs = Duration.between(start, end).toMillis().toDouble()
            val range = to - from
            val currentTime = Duration.between(start, now).toMillis()
            val newValue = (currentTime / durationMs * range).roundToInt()
            if (lastValue != newValue) {
                lastValue = newValue
            }
            val rateMs = durationMs / range
            val next = (newValue - from + 1) * rateMs


            println(newValue)
            val nextTs: LocalTime = start.plus(next.toLong(), ChronoUnit.MILLIS)
            println("$now $nextTs")

            delay(Duration.between(now.toLocalTime(), nextTs).toMillis())
            continue
        }

    }
}

fun main() {
    val phase = TimedLinearSequence(LocalTime.of(13, 22), LocalTime.of(13, 24), 0, 200)
    runBlocking {
        phase.run()
    }
}
