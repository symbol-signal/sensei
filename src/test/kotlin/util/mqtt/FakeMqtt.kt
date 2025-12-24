package symsig.sensei.util.mqtt

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Suback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.io.bytestring.encodeToByteString

class FakeMqtt : Mqtt {
    private val _publishedPackets = MutableSharedFlow<Publish>(replay = 1)
    override val publishedPackets: SharedFlow<Publish> = _publishedPackets

    override suspend fun subscribe(filters: List<TopicFilter>): Result<Suback> {
        return Result.success(Suback(1u, listOf(GrantedQoS0)))
    }

    override suspend fun publish(request: PublishRequest): Result<PublishResponse> {
        val dummyPublish = Publish(topic = Topic("dummy"), payload = "".encodeToByteString())
        return Result.success(AtMostOncePublishResponse(dummyPublish))
    }

    fun emitState(topic: String, payload: String): Boolean {
        return _publishedPackets.tryEmit(
            Publish(topic = Topic(topic), payload = payload.encodeToByteString())
        )
    }
}
