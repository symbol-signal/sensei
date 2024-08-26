package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.util.json.jsonPathExtractor

private val log = KotlinLogging.logger {}

class PresenceSensor(
    val sensorId: String,
    private val sensorIdExtractor: (JsonObject) -> String? = jsonPathExtractor(JsonPaths.SENSOR_ID),
    private val presenceExtractor: (JsonObject) -> Boolean? = jsonPathExtractor(JsonPaths.PRESENCE),
    private val timestampExtractor: ((JsonObject) -> String?)? = jsonPathExtractor(JsonPaths.TIMESTAMP)
) {
    var presence: Boolean = false
        private set

    fun messageReceived(jsonMessage: JsonMessage) {
        val messageBody: JsonObject = jsonMessage.payload

        if (sensorId != sensorIdExtractor(messageBody)) return

        val presenceValue = presenceExtractor(messageBody)
        if (presenceValue == null) {
            log.warn { "[missing_value] sensor=[$sensorId] value=[presence] message=[$messageBody]" }
            return
        }

        val timestamp = timestampExtractor?.invoke(messageBody)
        if (timestamp == null && timestampExtractor != null) {
            log.warn { "[missing_value] sensor=[$sensorId] value=[timestamp] message=[$messageBody]" }
        }

        if (presence != presenceValue) {
            presenceChanged(presenceValue)
        }
    }

    private fun presenceChanged(newPresence: Boolean) {
        presence = newPresence
        // Implement additional logic when presence changes, if necessary
    }

    companion object {
        object JsonPaths {
            const val SENSOR_ID = "sensorId"
            const val PRESENCE = "eventData.presence"
            const val TIMESTAMP = "eventAt"
        }
    }
}