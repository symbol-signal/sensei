package symsig.sensei

import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

interface DimmerChannel {

    suspend fun turnOn(brightness: Int? = null)

    suspend fun turnOff()
}

class DelayableChannel(
    private val channel: DimmerChannel,
    scope: CoroutineScope,
    private val defaultDelay: Duration = Duration.ZERO
) : DimmerChannel {
    private val scheduler = DebounceScheduler(scope)

    override suspend fun turnOn(brightness: Int?) = turnOn(defaultDelay, brightness)
    override suspend fun turnOff() = turnOff(defaultDelay)

    fun turnOn(delay: Duration = defaultDelay, brightness: Int? = null) {
        scheduler.schedule(delay) { channel.turnOn(brightness) }
    }

    fun turnOff(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { channel.turnOff() }
    }

    fun cancel() = scheduler.cancel()
}

/**
 * A composite dimmer channel that controls multiple channels as a single unit.
 *
 * This class implements the Composite pattern, allowing multiple [DimmerChannel]
 * instances to be controlled together. All operations are executed sequentially
 * on each channel in the order they were provided.
 *
 * @property channels The list of channels to control together
 *
 * @constructor Creates a combined channel from a list of channels
 */
class CombinedChannel(private val channels: List<DimmerChannel>) : DimmerChannel {
    /**
     * Creates a combined channel from individual channel instances.
     *
     * @param channels Variable number of channels to combine
     */
    constructor(vararg channels: DimmerChannel) : this(channels.toList())

    /**
     * Turns on all channels sequentially.
     */
    override suspend fun turnOn(brightness: Int?) {
        channels.forEach { it.turnOn(brightness) }
    }

    /**
     * Turns off all channels sequentially.
     */
    override suspend fun turnOff() {
        channels.forEach { it.turnOff() }
    }
}
