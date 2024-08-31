package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.util.json.jsonPathExtractor
import symsig.sensei.util.json.jsonPathExtractorAny
import symsig.sensei.util.misc.andThen
import symsig.sensei.util.misc.interpretAsBoolean
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_INSTANT
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

interface PresenceSensor {

    val sensorId: String
    val presence: Presence
    val lastChanged: Instant?

    fun addListener(listener: (PresenceChangeEvent) -> Unit)
    fun removeListener(listener: (PresenceChangeEvent) -> Unit)
}

interface PresenceSensorUpdatable : PresenceSensor {

    fun requestUpdate()
}

open class PresenceSensorEventDriven(override val sensorId: String) : PresenceSensor {

    override var presence: Presence = Presence.UNKNOWN
        protected set

    override var lastChanged: Instant? = null
        protected set

    private val listeners = CopyOnWriteArrayList<(PresenceChangeEvent) -> Unit>()

    fun newUpdate(message: PresenceChangeEvent) {
        if (message.sensorId != sensorId) {
            throw IllegalArgumentException("Sensor ID mismatch: expected [$sensorId], but got [${message.sensorId}]")
        }

        val newPresence = message.presence
        val updatedAt = message.changedAt

        if (newPresence == presence) return

        presence = newPresence
        lastChanged = updatedAt

        notifyListeners(message)
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

class MissingPresenceEventJsonFieldException(val sensorId: String, val field: String) : Exception()

class PresenceSensorJsonPathMessageProcessor(
    sensorIdPath: String, presencePath: String, timestampPath: String? = null
) {

    private val sensorIdExtractor = jsonPathExtractor<String>(sensorIdPath)
    private val presenceExtractor = jsonPathExtractorAny(presencePath)
    private val timestampExtractor = timestampPath?.let { jsonPathExtractor<Instant>(it) }

    operator fun invoke(sensorMessage: JsonMessage): PresenceChangeEvent? {
        val messageBody: JsonObject = sensorMessage.payload

        val sensorId: String = sensorIdExtractor(messageBody)
            ?: return null  // Return null if sensorId is null (indicating it wasn't found)

        val presenceValue =
            presenceExtractor(messageBody) ?: throw MissingPresenceEventJsonFieldException(sensorId, "presence")

        val timestamp: Instant = if (timestampExtractor != null) {
            timestampExtractor?.invoke(messageBody) ?: throw MissingPresenceEventJsonFieldException(
                sensorId, "timestamp"
            )
        } else {
            sensorMessage.timestamp
        }

        return PresenceChangeEvent(sensorId, Presence.parseValue(presenceValue), timestamp)
    }
}

open class PresenceSensorJson(
    override val sensorId: String,
    private val messageProcessor: (JsonMessage) -> PresenceChangeEvent?,
) : PresenceSensorEventDriven(sensorId) {

    fun handleSensorJsonMessage(jsonMessage: JsonMessage) {
        val sensorEvent: PresenceChangeEvent?

        try {
            sensorEvent = messageProcessor(jsonMessage) ?: return
        } catch (e: MissingPresenceEventJsonFieldException) {
            if (e.sensorId == sensorId) {
                log.warn { "[invalid_sensor_payload] sensor=[${e.sensorId}] missing_field=[${e.field}] payload=[$jsonMessage]" }
            }
            return
        }

        if (sensorEvent.sensorId != sensorId) return

        newUpdate(sensorEvent)
    }
}

open class PresenceSensorJsonUpdatable(sensorId: String,
                                       messageProcessor: (JsonMessage) -> PresenceChangeEvent?,
                                       private val updateRequestHandler: (String) -> Unit,
) : PresenceSensorJson(sensorId, messageProcessor), PresenceSensorUpdatable {

    override fun requestUpdate() {
        updateRequestHandler(sensorId)
    }
}

object PresenceSensors {

    fun sensord(
        sensorId: String,
        sensorMessageSender: (JsonObject) -> Unit
    ): PresenceSensorJsonUpdatable {
        val msgProcessor = PresenceSensorJsonPathMessageProcessor("sensorId", "eventData.presence", "eventAt")
        val updateRequestHandler: (String) -> Unit = UpdateRequestBuilders.sensord().andThen(sensorMessageSender)
        return PresenceSensorJsonUpdatable(sensorId, msgProcessor::invoke, updateRequestHandler)
    }
}

object UpdateRequestBuilders {

    fun sensord(): (String) -> JsonObject {
        return { sensorId ->
            buildJsonObject {
                put("sensorId", JsonPrimitive(sensorId))
                put("requestedAt", JsonPrimitive(ISO_INSTANT.format(Instant.now())))
            }
        }
    }
}
