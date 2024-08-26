import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.util.json.jsonPathExtractor
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val log = KotlinLogging.logger {}

data class PresenceChangeEvent(
    val presence: Boolean,
    val eventAt: Instant?,
)

class PresenceSensor(
    val sensorId: String,
    private val sensorIdExtractor: (JsonObject) -> String?,
    private val presenceExtractor: (JsonObject) -> Boolean?,
    private val timestampExtractor: ((JsonObject) -> Instant?)? = null
) {
    var presence: Boolean = false
        private set

    var lastChanged: Instant? = null
        private set

    private val listeners = CopyOnWriteArrayList<(PresenceChangeEvent) -> Unit>()

    fun messageReceived(jsonMessage: JsonMessage) {
        val messageBody: JsonObject = jsonMessage.payload

        if (sensorId != sensorIdExtractor(messageBody)) return

        val presenceValue = presenceExtractor(messageBody)
        if (presenceValue == null) {
            log.warn { "[missing_value] sensor=[$sensorId] value=[presence] message=[$messageBody]" }
            return
        }

        if (presence == presenceValue) return

        presence = presenceValue

        val timestamp = timestampExtractor?.invoke(messageBody)
        lastChanged = timestamp
        if (timestamp == null && timestampExtractor != null) {
            log.warn { "[missing_value] sensor=[$sensorId] value=[timestamp] message=[$messageBody]" }
        }

        notifyListeners(PresenceChangeEvent(presenceValue, timestamp))
    }

    private fun notifyListeners(event: PresenceChangeEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (e: Exception) {
                log.error(e) { "[listener_error] sensor=[$sensorId] listener=[$listener]" }
            }
        }
    }

    fun addListener(listener: (PresenceChangeEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (PresenceChangeEvent) -> Unit) {
        listeners.remove(listener)
    }
}

object PresenceSensors {

    fun sensord(sensorId: String): PresenceSensor {
        return PresenceSensor(
            sensorId = sensorId,
            sensorIdExtractor = jsonPathExtractor("sensorId"),
            presenceExtractor = jsonPathExtractor("eventData.presence"),
            timestampExtractor = jsonPathExtractor("eventAt")
        )
    }
}
