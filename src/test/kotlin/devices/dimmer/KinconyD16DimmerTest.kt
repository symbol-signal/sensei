package symsig.sensei.devices.dimmer

import de.kempmobil.ktor.mqtt.AtMostOncePublishResponse
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.PublishResponse
import de.kempmobil.ktor.mqtt.GrantedQoS0
import de.kempmobil.ktor.mqtt.Topic
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Suback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import symsig.sensei.mqtt.Mqtt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KinconyD16DimmerTest {

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

        fun emitStateSync(topic: String, json: String): Boolean {
            return _publishedPackets.tryEmit(
                Publish(topic = Topic(topic), payload = json.encodeToByteString())
            )
        }
    }

    @Test
    fun `mapFromRange with effective range 20 to 40`() = runTest(UnconfinedTestDispatcher()) {
        val mqtt = FakeMqtt()
        val dimmer = KinconyD16Dimmer(
            mqtt, "dimmer/state", "dimmer/set", backgroundScope,
            effectiveRanges = mapOf(Channel.Ch1 to 20..40)
        )

        fun assertMaps(hardwareValue: Int, expectedLogical: Int, description: String) {
            mqtt.emitStateSync("dimmer/state", """{"dimmer1":{"value":$hardwareValue}}""")
            assertEquals(expectedLogical, dimmer.state.value[Channel.Ch1], description)
        }

        // Zero is always off
        assertMaps(0, 0, "hw=0 → off")

        // Below range minimum - light is on, should map to minimum "on" value
        assertMaps(1, 1, "hw=1 → on (below range)")
        assertMaps(19, 1, "hw=19 → on (just below range)")

        assertMaps(20, 1, "hw=20 → on (range start)")  // At range minimum - light is on, maps to 1
        assertMaps(30, 50, "hw=30 → 50 (range middle)")
        assertMaps(40, 99, "hw=40 → 99 (range end)")
        assertMaps(50, 99, "hw=50 → 99 (above range)")
    }
}
