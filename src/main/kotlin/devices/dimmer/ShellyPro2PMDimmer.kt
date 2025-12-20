package symsig.sensei.devices.dimmer

import de.kempmobil.ktor.mqtt.PublishRequest
import symsig.sensei.mqtt.Mqtt
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import symsig.sensei.CombinedChannel
import symsig.sensei.DimmerChannel

/**
 * Controls a Shelly Pro Dimmer 2PM device via MQTT using the Shelly RPC protocol.
 *
 * This dimmer supports two independent channels that can be controlled with
 * brightness levels from 0-100.
 *
 * @see <a href="https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/Light">Shelly Light API Doc</a>
 *
 * @property mqtt The MQTT client for communication
 * @property topic The MQTT topic for this device (typically "{id}/rpc")
 * @property scope The coroutine scope for async operations
 */
class ShellyPro2PMDimmer(private val mqtt: Mqtt, private val topic: String, scope: CoroutineScope) {

    @Serializable
    data class LightSetParams(val id: Int, val on: Boolean? = null, val brightness: Int? = null)

    @Serializable
    data class ShellyRpc(val src: String = "cli", val method: String, val params: LightSetParams)

    enum class Channel(val id: Int) {
        Ch1(0), Ch2(1)
    }

    inner class ShellyPro2PMChannel(val channel: Channel) : DimmerChannel {

        private val json = Json { encodeDefaults = true }

        override suspend fun turnOn(brightness: Int?) {
            sendSwitchCmd(true, brightness)
        }

        override suspend fun turnOff() {
            sendSwitchCmd(false)
        }

        suspend fun sendSwitchCmd(on: Boolean, brightness: Int? = null) {
            val brightnessVal = if (on) brightness else null
            val message = ShellyRpc(method = "Light.Set", params = LightSetParams(channel.id, on, brightnessVal))
            mqtt.publish(PublishRequest(topic) {
                payload(json.encodeToString(message))
            })
        }

        override suspend fun toggle() {
            val message = ShellyRpc(method = "Light.Toggle", params = LightSetParams(channel.id))
            mqtt.publish(PublishRequest(topic) {
                payload(json.encodeToString(message))
            })
        }

        override suspend fun setBrightness(value: Int) {
            val message = ShellyRpc(method = "Light.Set", params = LightSetParams(channel.id, brightness = value))
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