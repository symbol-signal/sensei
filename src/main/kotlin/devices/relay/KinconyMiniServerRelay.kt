package symsig.sensei.devices.relay

import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import symsig.sensei.devices.Switchable
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import symsig.sensei.util.mqtt.Mqtt
import java.util.Locale

enum class RelayState {
    ON, OFF, UNKNOWN;

    companion object {
        fun parse(bytes: ByteString): RelayState = when (bytes.decodeToString().trim().lowercase(Locale.ROOT)) {
            "on", "1", "true" -> ON
            "off", "0", "false" -> OFF
            else -> UNKNOWN
        }
    }
}

/**
 * Relay implementation for Kincony Mini Server relays controlled via synaps.
 *
 * @param mqtt MQTT client
 * @param stateTopic Topic to subscribe for state updates (e.g., "home/kitchen/light/state")
 * @param commandTopic Topic to publish commands to (e.g., "home/kitchen/light/command")
 * @param scope Coroutine scope for state flow
 */
class KinconyMiniServerRelay(
    private val mqtt: Mqtt,
    private val stateTopic: String,
    private val commandTopic: String,
    scope: CoroutineScope
) : Relay, Switchable {

    val state: StateFlow<RelayState> =
        mqtt.publishedPackets
            .filter { it.topic.name == stateTopic }
            .map { RelayState.parse(it.payload) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, RelayState.UNKNOWN)

    override val isOn: StateFlow<Boolean> =
        state.map { it == RelayState.ON }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch { mqtt.subscribe(buildFilterList { +stateTopic }) }
    }

    override suspend fun turnOn() {
        mqtt.publish(PublishRequest(commandTopic) { payload("ON") })
    }

    override suspend fun turnOff() {
        mqtt.publish(PublishRequest(commandTopic) { payload("OFF") })
    }

    override suspend fun toggle() {
        mqtt.publish(PublishRequest(commandTopic) { payload("TOGGLE") })
    }
}
