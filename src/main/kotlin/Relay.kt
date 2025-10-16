package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


interface Relay {
    suspend fun toggle()
}

class ShellyPlus1PMRelay(private val mqtt: MqttClient, private val topic: String, scope: CoroutineScope) : Relay {

    @Serializable
    data class ShellyRpc(val src: String = "cli", val method: String, val params: Map<String, Int>)

    private val json = Json { encodeDefaults = true }

    override suspend fun toggle() {
        val message = ShellyRpc(method = "Switch.Toggle", params = mapOf("id" to 0))
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }
}