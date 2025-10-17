package symsig.sensei

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

class DebounceScheduler(private val scope: CoroutineScope) {

    private var currentJob: Job? = null

    fun schedule(delay: Duration, action: suspend () -> Unit) {
        currentJob?.cancel()
        currentJob = scope.launch {
            delay(delay)
            action()
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}