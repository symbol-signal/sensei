package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

interface DimmerChannel {

    suspend fun turnOn()

    suspend fun turnOff()
}

class DelayableChannel(private val channel: DimmerChannel, scope: CoroutineScope) : DimmerChannel {
    private val scheduler = DebounceScheduler(scope)

    override suspend fun turnOn() = turnOn(Duration.ZERO)
    override suspend fun turnOff() = turnOff(Duration.ZERO)

    fun turnOn(delay: Duration) {
        scheduler.schedule(delay) { channel.turnOn() }
    }

    fun turnOff(delay: Duration) {
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
    override suspend fun turnOn() {
        channels.forEach { it.turnOn() }
    }

    /**
     * Turns off all channels sequentially.
     */
    override suspend fun turnOff() {
        channels.forEach { it.turnOff() }
    }
}

class ShellyPro2PMDimmer(private val mqtt: MqttClient, private val topic: String, scope: CoroutineScope) {

    @Serializable
    data class LightSetParams(val id: Int, val on: Boolean)

    @Serializable
    data class ShellyRpc(val src: String = "cli", val method: String, val params: LightSetParams)

    enum class Channel(val id: Int) {
        Ch1(0), Ch2(1)
    }

    inner class ShellyPro2PMChannel(val channel: Channel) : DimmerChannel {

        private val json = Json { encodeDefaults = true }

        override suspend fun turnOn() {
            sendSwitchCmd(true)
        }

        override suspend fun turnOff() {
            sendSwitchCmd(false)
        }

        suspend fun sendSwitchCmd(on: Boolean) {
            val message = ShellyRpc(method = "Light.Set", params = LightSetParams(channel.id, on))
            mqtt.publish(PublishRequest(topic) {
                payload(json.encodeToString(message))
            })
        }
    }

    fun channel(channel: Channel): DimmerChannel {
        return ShellyPro2PMChannel(channel)
    }

    /**
     * Gets a controller that operates on all channels simultaneously.
     *
     * @return A [DimmerChannel] that controls all channels together
     */
    fun allChannels(): DimmerChannel {
        return CombinedChannel(Channel.entries.map { channel(it) })
    }
}
