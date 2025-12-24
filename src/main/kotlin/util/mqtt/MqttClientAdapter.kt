package symsig.sensei.util.mqtt

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.PublishResponse
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.packet.Suback

class MqttClientAdapter(private val client: MqttClient) : Mqtt {
    override val publishedPackets = client.publishedPackets
    override suspend fun subscribe(filters: List<TopicFilter>): Result<Suback> = client.subscribe(filters)
    override suspend fun publish(request: PublishRequest): Result<PublishResponse> = client.publish(request)
}
