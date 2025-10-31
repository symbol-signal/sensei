package symsig.sensei.devices.dimmer

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import symsig.sensei.CombinedChannel
import symsig.sensei.DimmerChannel

private val DEFAULT_RANGE = 0..99


class KinconyD16Dimmer(
    private val mqtt: MqttClient,
    private val topic: String,
    scope: CoroutineScope,
    effectiveRanges: Map<Channel, IntRange> = mapOf()) {

    private val effectiveRanges: Map<Channel, IntRange> = effectiveRanges.toMap()

    enum class Channel(val id: Int) {
        Ch1(1), Ch2(2), Ch3(3), Ch4(4), Ch5(5), Ch6(6), Ch7(7), Ch8(8),
        Ch9(9), Ch10(10), Ch11(11), Ch12(12), Ch13(13), Ch14(14), Ch15(15), Ch16(16)
    }

    private fun mapToRange(value: Int, range: IntRange): Int {
        if (value == 0) {
            return 0  // Use 0 for turning off
        }
        val rangeLength = range.last - range.first
        return ((rangeLength / 99.0) * value + range.first).toInt()
    }

    inner class KinconyD16Channel(val channel: Channel) : DimmerChannel {

        private val json = Json { encodeDefaults = true }

        override suspend fun turnOn(brightness: Int?) {
            sendDimmerValue(brightness ?: 99)
        }

        override suspend fun turnOff() {
            sendDimmerValue(0)
        }

        override suspend fun toggle() {
            TODO("Not yet implemented")
        }

        suspend fun sendDimmerValue(value: Int) {
            require(value in 0..99) { "Dimmer value must be between 0 and 99, got: $value" }
            val effectiveVal = mapToRange(value, effectiveRanges[channel] ?: DEFAULT_RANGE)
            mqtt.publish(PublishRequest(topic) {
                payload(json.encodeToString(mapOf("dimmer${channel.id}" to mapOf("value" to effectiveVal))))
            })
        }
    }

    fun channel(channel: Channel): KinconyD16Channel {
        return KinconyD16Channel(channel)
    }

    fun channels(vararg channels: Channel): CombinedChannel {
        return CombinedChannel(channels.map { channel(it) })
    }
}