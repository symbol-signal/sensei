package symsig.sensei

import de.kempmobil.ktor.mqtt.PublishRequest
import symsig.sensei.mqtt.Mqtt
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import symsig.sensei.util.schedule.DebounceScheduler
import kotlin.time.Duration


interface Relay {
    suspend fun turnOn()
    suspend fun turnOff()
    suspend fun toggle()
}

class ShellyPlus1PMRelay(private val mqtt: Mqtt, private val topic: String, scope: CoroutineScope) : Relay {

    @Serializable
    data class ToggleParams(val id: Int = 0)

    @Serializable
    data class SetParams(val id: Int = 0, val on: Boolean)

    @Serializable
    data class ShellyRpc<T>(val src: String = "cli", val method: String, val params: T)

    private val json = Json { encodeDefaults = true }

    override suspend fun turnOn() {
        val message = ShellyRpc(method = "Switch.Set", params = SetParams(on = true))
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }

    override suspend fun turnOff() {
        val message = ShellyRpc(method = "Switch.Set", params = SetParams(on = false))
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }

    override suspend fun toggle() {
        val message = ShellyRpc(method = "Switch.Toggle", params = ToggleParams())
        mqtt.publish(PublishRequest(topic) {
            payload(json.encodeToString(message))
        })
    }
}

class DelayableRelay(
    private val relay: Relay,
    scope: CoroutineScope,
    private val defaultDelay: Duration = Duration.ZERO
) : Relay {
    private val scheduler = DebounceScheduler(scope)

    override suspend fun turnOn() = turnOn(defaultDelay)
    override suspend fun turnOff() = turnOff(defaultDelay)
    override suspend fun toggle() = toggle(defaultDelay)

    fun turnOn(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.turnOn() }
    }

    fun turnOff(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.turnOff() }
    }

    fun toggle(delay: Duration = defaultDelay) {
        scheduler.schedule(delay) { relay.toggle() }
    }

    fun cancel() = scheduler.cancel()
}