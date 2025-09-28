package symsig

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.flow.take
import kotlinx.io.bytestring.decodeToString

suspend fun main() {
    val client = MqttClient("central.local", 1883) { /* auth/logging if you need */ }

    try {
        val connAck = client.connect().getOrThrow()
        require(connAck.isSuccess) { "MQTT connect failed: $connAck" }

        client.subscribe(buildFilterList { +"#" })

        client.publishedPackets
            .take(10)
            .collect { publish ->
                println("Received: ${publish.topic} -> ${publish.payload.decodeToString()}")
            }
    } finally {
        client.disconnect()
    }
}
