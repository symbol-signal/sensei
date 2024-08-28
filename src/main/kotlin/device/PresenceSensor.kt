package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.util.json.jsonPathExtractor
import symsig.sensei.util.json.jsonPathExtractorAny
import symsig.sensei.util.misc.interpretAsBoolean
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val log = KotlinLogging.logger {}

enum class Presence {

    PRESENT, ABSENT, UNKNOWN;

    companion object {

        fun parseAsBoolean(value: Boolean?): Presence {
            return when (value) {
                true -> PRESENT
                false -> ABSENT
                null -> UNKNOWN
            }
        }

        fun parseValue(value: Any?): Presence {
            return parseAsBoolean(interpretAsBoolean(value))
        }
    }
}


data class PresenceChangeEvent(
    val sensorId: String,
    val presence: Presence,
    val changedAt: Instant,
)

// TODO Move to more generic class
interface MessageDriven {

    /**
     * Requests an update from the sensor.
     *
     * @throws UpdateRequestNotAvailableException if the sensor cannot handle update requests.
     */
    fun requestUpdate()
}

class UpdateRequestNotAvailableException(sensorId: String) :
    Exception("Sensor $sensorId does not support requesting an update")

interface PresenceSensor {

    val sensorId: String
    val presence: Presence
    val lastChanged: Instant?

    fun addListener(listener: (PresenceChangeEvent) -> Unit)
    fun removeListener(listener: (PresenceChangeEvent) -> Unit)
}

interface PresenceSensorMessageDriven : PresenceSensor, MessageDriven

abstract class AbstractPresenceSensor : PresenceSensor {

    override var presence: Presence = Presence.UNKNOWN
        protected set

    override var lastChanged: Instant? = null
        protected set

    private val listeners = CopyOnWriteArrayList<(PresenceChangeEvent) -> Unit>()

    fun newUpdate(newPresence: Presence, updatedAt: Instant) {
        if (newPresence == presence) return

        presence = newPresence
        lastChanged = updatedAt

        notifyListeners(PresenceChangeEvent(sensorId, newPresence, updatedAt))
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

    override fun addListener(listener: (PresenceChangeEvent) -> Unit) {
        listeners.add(listener)
    }

    override fun removeListener(listener: (PresenceChangeEvent) -> Unit) {
        listeners.remove(listener)
    }
}

data class PresenceSensorMessage(
    val sensorId: String,
    val presence: Presence,
    val changedAt: Instant,
)

class MissingPresenceEventJsonFieldException(var sensorId: String, var field: String) : Exception()

class PresenceSensorJsonPathMessageProcessor(
    sensorIdPath: String,
    presencePath: String,
    timestampPath: String? = null
) {

    private var sensorIdExtractor = jsonPathExtractor<String>(sensorIdPath)
    private var presenceExtractor = jsonPathExtractorAny(presencePath)
    private var timestampExtractor = timestampPath?.let { jsonPathExtractor<Instant>(it) }

    operator fun invoke(sensorMessage: JsonMessage): PresenceSensorMessage? {
        val messageBody: JsonObject = sensorMessage.payload

        val sensorId: String = sensorIdExtractor(messageBody)
            ?: return null  // Return null if sensorId is null (indicating it wasn't found)

        val presenceValue = presenceExtractor(messageBody)
            ?: throw MissingPresenceEventJsonFieldException(sensorId, "presence")

        val timestamp: Instant = if (timestampExtractor != null) {
            timestampExtractor?.invoke(messageBody) ?: throw MissingPresenceEventJsonFieldException(
                sensorId,
                "timestamp"
            )
        } else {
            sensorMessage.timestamp
        }

        return PresenceSensorMessage(sensorId, Presence.parseValue(presenceValue), timestamp)
    }
}

class PresenceSensorJson(override var sensorId: String, private var messageProcessor: (JsonMessage) -> PresenceSensorMessage?) :
    AbstractPresenceSensor(), PresenceSensorMessageDriven {

    fun handleSensorJsonMessage(jsonMessage: JsonMessage) {
        val sensorMessage: PresenceSensorMessage?

        try {
            sensorMessage = messageProcessor(jsonMessage) ?: return
        } catch (e: MissingPresenceEventJsonFieldException) {
            if (e.sensorId == sensorId) {
                log.warn { "[invalid_sensor_payload] sensor=[${e.sensorId}] missing_field=[${e.field}] payload=[$jsonMessage]" }
            }
            return
        }

        if (sensorMessage.sensorId != sensorId) return

        newUpdate(sensorMessage.presence, sensorMessage.changedAt)
    }

    override fun requestUpdate() {
        TODO("Not yet implemented")
    }
}


object PresenceSensors {

    fun sensord(sensorId: String): PresenceSensorMessageDriven {
        val msgProcessor = PresenceSensorJsonPathMessageProcessor("sensorId", "eventData.presence", "eventAt")
        return PresenceSensorJson(sensorId, msgProcessor::invoke)
    }
}
