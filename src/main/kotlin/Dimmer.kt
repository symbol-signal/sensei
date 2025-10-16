package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface DimmerChannel {

    suspend fun turnOn()

    suspend fun turnOff()
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
}
