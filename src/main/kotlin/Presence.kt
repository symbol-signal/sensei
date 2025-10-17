package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import java.util.Locale
import kotlin.time.Duration

enum class PresenceState {
    PRESENT, ABSENT, UNKNOWN;

    companion object {
        fun parse(bytes: ByteString): PresenceState = when (bytes.decodeToString().trim().lowercase(Locale.ROOT)) {
            "present", "on", "1", "true" -> PRESENT
            "absent", "off", "0", "false" -> ABSENT
            else -> UNKNOWN
        }
    }
}

class PresenceSensor(
    private val mqtt: MqttClient,
    val topic: String,
    scope: CoroutineScope,
    val presentDelay: Duration = Duration.ZERO,
    val absentDelay: Duration = Duration.ZERO) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PresenceState> =
        mqtt.publishedPackets
            .filter { it.topic.name == topic }
            .map { PresenceState.parse(it.payload) }
            .distinctUntilChanged()
            .transformLatest { newState ->
                val delay = when (newState) {
                    PresenceState.PRESENT -> presentDelay
                    PresenceState.ABSENT -> absentDelay
                    PresenceState.UNKNOWN -> Duration.ZERO
                }

                if (delay > Duration.ZERO) {
                    delay(delay)
                }

                emit(newState)
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(), PresenceState.UNKNOWN)

    init {
        scope.launch { mqtt.subscribe(buildFilterList { +topic }) }
    }
}

class CombinedPresenceSensor(private val sensors: List<PresenceSensor>, scope: CoroutineScope) {

    constructor(vararg sensors: PresenceSensor, scope: CoroutineScope) : this(sensors.toList(), scope)

    val state: StateFlow<PresenceState> = combine(sensors.map { it.state }) { states ->
        when {
            states.any { it == PresenceState.PRESENT } -> PresenceState.PRESENT
            states.all { it == PresenceState.ABSENT } -> PresenceState.ABSENT
            else -> PresenceState.UNKNOWN
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), PresenceState.UNKNOWN)
}
