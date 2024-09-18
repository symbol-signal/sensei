package symsig.sensei.util.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class DebounceScheduler(private val scope: CoroutineScope) {

    private val currentJob = AtomicReference<Job?>(null)

    fun schedule(delay: Duration, action: suspend () -> Unit) {
        scope.launch {
            val previousJob = currentJob.getAndSet(coroutineContext[Job])
            previousJob?.let { job ->
                job.cancel()
                job.join()
            }

            delay(delay.toMillis())
            action()
            currentJob.compareAndSet(coroutineContext[Job], null)
        }
    }
}
