package symsig.sensei.devices.relay

import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import symsig.sensei.util.mqtt.Mqtt

class ShellyPlus1PMRelay(private val mqtt: Mqtt, private val topic: String, scope: CoroutineScope) : Relay {

    @Serializable
    data class ToggleParams(val id: Int = 0)

    @Serializable
    data class SetParams(val id: Int = 0, val on: Boolean)

    @Serializable
    data class ShellyRpc<T>(val src: String = "cli", val method: String, val params: T)

    private val json = Json { encodeDefaults = true }

    override suspend fun turnOn() {
        val message = ShellyRpc(method = "Switch.Set", params = SetParams(on = true))
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }

    override suspend fun turnOff() {
        val message = ShellyRpc(method = "Switch.Set", params = SetParams(on = false))
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }

    override suspend fun toggle() {
        val message = ShellyRpc(method = "Switch.Toggle", params = ToggleParams())
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }
}