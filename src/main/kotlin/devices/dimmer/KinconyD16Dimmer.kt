package symsig.sensei.devices.dimmer

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import symsig.sensei.CombinedChannel
import symsig.sensei.DimmerChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private val DEFAULT_RANGE = 0..99


private fun checkDimmerValue(value: Int) {
    require(value in 0..99) { "Dimmer value must be between 0 and 99, got: $value" }
}

class KinconyD16Dimmer(
    private val mqtt: MqttClient,
    private val stateTopic: String,
    private val setTopic: String,
    private val scope: CoroutineScope,
    effectiveRanges: Map<Channel, IntRange> = mapOf()
) {

    val state: StateFlow<Map<Channel, Int>> =
        mqtt.publishedPackets
            .filter { it.topic.name == stateTopic }
            .map { parseDimmerStates(it.payload) }
            .stateIn(scope, SharingStarted.WhileSubscribed(), Channel.entries.associateWith { 0 })

    private val channelInstances = ConcurrentHashMap<Channel, KinconyD16Channel>()

    private val effectiveRanges: Map<Channel, IntRange> = effectiveRanges.toMap()

    private val json = Json

    enum class Channel(val id: Int) {
        Ch1(1), Ch2(2), Ch3(3), Ch4(4), Ch5(5), Ch6(6), Ch7(7), Ch8(8),
        Ch9(9), Ch10(10), Ch11(11), Ch12(12), Ch13(13), Ch14(14), Ch15(15), Ch16(16)
    }

    private fun mapToRange(value: Int, range: IntRange): Int {
        if (value == 0) {
            return 0  // Use 0 for turning off
        }
        val rangeLength = range.last - range.first
        return ((rangeLength / 99.0) * value + range.first).roundToInt()
    }

    private fun mapFromRange(value: Int, range: IntRange): Int {
        if (value <= range.first) return 0
        val rangeLength = range.last - range.first
        return (((value - range.first) / rangeLength.toDouble()) * 99).roundToInt().coerceIn(0, 99)
    }

    private fun parseDimmerStates(jsonString: ByteString): Map<Channel, Int> {
        val dimmerStateJson = json.parseToJsonElement(jsonString.decodeToString()).jsonObject
        return Channel.entries.associateWith { channel ->
            val hwValue = dimmerStateJson["dimmer${channel.id}"]?.jsonObject?.get("value")?.jsonPrimitive?.int ?: 0
            mapFromRange(hwValue, effectiveRanges[channel] ?: DEFAULT_RANGE)  // Map back to logical 0-99 range
        }
    }

    fun channel(channel: Channel): KinconyD16Channel {
        return channelInstances.computeIfAbsent(channel) { ch ->
            val brightness = state
                .map { it[ch] ?: 0 }
                .distinctUntilChanged()
                .stateIn(scope, SharingStarted.WhileSubscribed(5000), state.value[ch] ?: 0)
            KinconyD16Channel(ch, brightness)
        }
    }

    fun channels(vararg channels: Channel): CombinedChannel {
        return CombinedChannel(channels.map { channel(it) })
    }

    inner class KinconyD16Channel(val channel: Channel, val brightness: StateFlow<Int>) : DimmerChannel {

        val isOn: StateFlow<Boolean> = brightness
            .map { it > 0 }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), brightness.value > 0)

        private val _lastSetBrightness = MutableStateFlow(99)
        val lastSetBrightness: StateFlow<Int> get() = _lastSetBrightness

        private val json = Json { encodeDefaults = true }

        init {
            brightness
                .filter { it > 0 }
                .onEach { _lastSetBrightness.value = it }
                .launchIn(scope)
        }

        override suspend fun turnOn(brightness: Int?) {
            sendDimmerValue(brightness ?: lastSetBrightness.value)
        }

        override suspend fun turnOff() {
            sendDimmerValue(0)
        }

        override suspend fun toggle() {
            if (isOn.value) {
                turnOff()
            } else {
                turnOn()
            }
        }

        override suspend fun setBrightness(value: Int) {
            checkDimmerValue(value)
            if (isOn.value) {
                sendDimmerValue(value)
            } else {
                _lastSetBrightness.value = value
            }
        }

        private suspend fun sendDimmerValue(value: Int) {
            checkDimmerValue(value)
            val effectiveVal = mapToRange(value, effectiveRanges[channel] ?: DEFAULT_RANGE)
            mqtt.publish(PublishRequest(setTopic) {
                payload(json.encodeToString(mapOf("dimmer${channel.id}" to mapOf("value" to effectiveVal))))
            })
        }
    }
}