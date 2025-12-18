package symsig.sensei.devices.dimmer

import io.github.oshai.kotlinlogging.KotlinLogging
import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

private val log = KotlinLogging.logger {}

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

    // Eagerly starts collecting immediately, ensuring retained MQTT messages are captured
    // even before any downstream subscribers exist. WhileSubscribed would miss retained
    // messages delivered on subscription if no one is subscribed to state yet.
    val state: StateFlow<Map<Channel, Int>> =
        mqtt.publishedPackets
            .filter { it.topic.name == stateTopic }
            .map { parseDimmerStates(it.payload) }
            .stateIn(scope, SharingStarted.Eagerly, Channel.entries.associateWith { 0 })

    init {
        scope.launch { mqtt.subscribe(buildFilterList { +stateTopic }) }
    }

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

    /**
     * Calculates the maximum roundtrip error when converting logical brightness (0-99)
     * to effective hardware value and back. Narrower ranges amplify rounding errors.
     *
     * Formula: ceil(0.5 * (99/rangeLength + 1))
     * - rangeLength=99 → tolerance=1
     * - rangeLength=41 → tolerance=2
     * - rangeLength=20 → tolerance=3
     */
    private fun roundtripTolerance(channel: Channel): Int {
        val range = effectiveRanges[channel] ?: DEFAULT_RANGE
        val rangeLength = range.last - range.first
        return kotlin.math.ceil(0.5 * (99.0 / rangeLength + 1)).toInt()
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

        /** Whether the light is currently on (brightness > 0). */
        val isOn: StateFlow<Boolean> = brightness
            .map { it > 0 }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), brightness.value > 0)

        /**
         * Last known non-zero brightness from hardware state.
         * Used as fallback when turning on without a specified brightness.
         */
        val lastBrightness: StateFlow<Int> = brightness
            .filter { it > 0 }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), 99)

        /**
         * Brightness value set programmatically via [setBrightness].
         *
         * When set while the light is off, this value will be automatically applied
         * when the light turns on (either programmatically or via physical switch).
         *
         * Also used for manual override detection: if [brightness] differs from this value
         * while the light is on, it indicates the user manually changed the brightness,
         * and subsequent [setBrightness] calls will be ignored until the next on/off cycle.
         */
        private var programmaticBrightness: Int? = null

        private val json = Json { encodeDefaults = true }

        init {
            brightness
                .drop(1)  // Skip initial StateFlow value, only log actual MQTT updates
                .onEach { value ->
                    log.debug { "kincony_dimmer_state channel=$channel brightness=$value" }
                }
                .launchIn(scope)

            // When light turns on, apply programmatic brightness if set.
            // This enables "set brightness while off, apply when turned on" behavior,
            // including when the light is turned on manually via physical switch.
            isOn
                .filter { it }
                .onEach {
                    programmaticBrightness?.let { target ->
                        if (brightness.value != target) {
                            log.debug { "kincony_brightness_reset channel=$channel brightness=$target" }
                            sendDimmerValue(target)
                        }
                    }
                }
                .launchIn(scope)
        }

        /**
         * Turns on the light with the specified brightness.
         *
         * Priority: [brightness] parameter > [programmaticBrightness] > [lastBrightness]
         */
        override suspend fun turnOn(brightness: Int?) {
            sendDimmerValue(brightness ?: programmaticBrightness ?: lastBrightness.value)
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
            setBrightness(value, forceOverride = false)
        }

        /**
         * Sets the target brightness for this channel.
         *
         * **When light is off:** The value is stored in [programmaticBrightness] and will be
         * applied when the light turns on (either programmatically or via physical switch).
         *
         * **When light is on:** The value is applied immediately, unless manual override is
         * detected. Manual override occurs when the current [brightness] differs from
         * [programmaticBrightness], indicating the user changed it via physical controls.
         * In this case, the call is ignored to respect user intent.
         *
         * **Manual override reset:** The override state resets when the light is turned off
         * and back on (a new "cycle"), allowing programmatic control to resume.
         *
         * @param value Brightness level (0-99)
         * @param forceOverride If true, bypasses manual override detection. Use this for
         *   intentional rapid consecutive changes (e.g., fade animations, UI sliders).
         */
        suspend fun setBrightness(value: Int, forceOverride: Boolean) {
            checkDimmerValue(value)

            val canSet = forceOverride ||
                    !isOn.value ||
                    programmaticBrightness == null ||
                    kotlin.math.abs(brightness.value - programmaticBrightness!!) <= roundtripTolerance(channel)

            // Always update the target, even if we can't apply it now.
            // This ensures the latest programmatic intent is applied on next on-cycle.
            programmaticBrightness = value

            if (canSet && isOn.value) {
                sendDimmerValue(value)
            }
        }

        private suspend fun sendDimmerValue(value: Int) {
            checkDimmerValue(value)
            val effectiveVal = mapToRange(value, effectiveRanges[channel] ?: DEFAULT_RANGE)
            log.debug { "kincony_dimmer_set channel=$channel value=$value effective=$effectiveVal current_brightness=${brightness.value}" }
            mqtt.publish(PublishRequest(setTopic) {
                payload(json.encodeToString(mapOf("dimmer${channel.id}" to mapOf("value" to effectiveVal))))
            })
        }
    }
}