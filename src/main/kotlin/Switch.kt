package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import java.util.Locale

enum class SwitchState {
    ON, OFF, UNKNOWN;

    companion object {
        fun parse(bytes: ByteString): SwitchState = when (bytes.decodeToString().trim().lowercase(Locale.ROOT)) {
            "on", "1", "true" -> ON
            "off", "0", "false" -> OFF
            else -> UNKNOWN
        }
    }
}

class Switch(private val mqtt: MqttClient, private val topic: String, scope: CoroutineScope) {

    val state: StateFlow<SwitchState> =
        mqtt.publishedPackets
            .filter { it.topic.name == topic }
            .map { SwitchState.parse(it.payload) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), SwitchState.UNKNOWN)

    init {
        scope.launch { mqtt.subscribe(buildFilterList { +topic }) }
    }
}
