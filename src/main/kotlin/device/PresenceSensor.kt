package symsig.sensei.device

import io.github.nomisrev.JsonPath
import io.github.nomisrev.boolean
import io.github.nomisrev.path
import io.github.nomisrev.string
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject

const val DEFAULT_SENSOR_ID_JSON_PATH = "sensorId"

const val DEFAULT_PRESENCE_JSON_PATH = "eventData.presence"

private val log = KotlinLogging.logger {}

class PresenceSensor(val sensorId: String,
                     val sensorIdJsonPath: String = DEFAULT_SENSOR_ID_JSON_PATH,
                     val presenceJsonPath: String = DEFAULT_PRESENCE_JSON_PATH) {

    fun messageReceived(messageBody: JsonObject) {
        val sensorId = JsonPath.path(sensorIdJsonPath).string.getOrNull(messageBody)
        if (this.sensorId != sensorId) {
            return
        }

        val presenceValue = JsonPath.path(presenceJsonPath).boolean.getOrNull(messageBody)
        if (presenceValue == null) {
            log.warn { "[missing_presence_value] sensor=[$sensorId] type=[presence] presence_path=[$presenceJsonPath]" }
            return
        }
    }
}

