package symsig.sensei.mqtt

import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.PublishResponse
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Suback
import kotlinx.coroutines.flow.SharedFlow

interface Mqtt {
    val publishedPackets: SharedFlow<Publish>
    suspend fun subscribe(filters: List<TopicFilter>): Result<Suback>
    suspend fun publish(request: PublishRequest): Result<PublishResponse>
}
