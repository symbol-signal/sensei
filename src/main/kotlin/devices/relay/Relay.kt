package symsig.sensei.devices.relay

import kotlinx.coroutines.CoroutineScope
import symsig.sensei.util.schedule.DebounceScheduler
import kotlin.time.Duration


interface Relay {
    suspend fun turnOn()
    suspend fun turnOff()
    suspend fun toggle()
}

class DelayableRelay(
    private val relay: Relay,
    scope: CoroutineScope,
    private val defaultDelay: Duration = Duration.ZERO
) : Relay {
    private val scheduler = DebounceScheduler(scope)

    override suspend fun turnOn() = turnOn(defaultDelay)
    override suspend fun turnOff() = turnOff(defaultDelay)
    override suspend fun toggle() = toggle(defaultDelay)

    fun turnOn(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.turnOn() }
    }

    fun turnOff(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.turnOff() }
    }

    fun toggle(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.toggle() }
    }

    fun cancel() = scheduler.cancel()
}