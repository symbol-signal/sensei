package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.`interface`.MessageHandlerResult
import symsig.sensei.`interface`.PresenceSensorRemoteMessaging
import symsig.sensei.`interface`.WebSocketMessageHandler
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

typealias PresenceChangeEventListener = (PresenceChangeEvent) -> Unit

interface PresenceSensor {

    val sensorId: String
    val presence: Presence
    val lastChanged: Instant?

    fun addListener(listener: PresenceChangeEventListener)
    fun removeListener(listener: PresenceChangeEventListener)
}

interface PresenceSensorUpdatable : PresenceSensor {

    fun requestUpdate()
}

class PresenceSensorEventDriven(override val sensorId: String) : PresenceSensor {

    override var presence: Presence = Presence.UNKNOWN
        private set

    override var lastChanged: Instant? = null
        private set

    private val listeners = CopyOnWriteArrayList<PresenceChangeEventListener>()

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

class PresenceSensorEventDrivenUpdatable(
    private val baseSensor: PresenceSensorEventDriven,
    private val updateRequestHandler: (String) -> Unit
) : PresenceSensor by baseSensor, PresenceSensorUpdatable {

    override fun requestUpdate() {
        updateRequestHandler(baseSensor.sensorId)
    }
}

class MissingJsonFieldException(val field: String) : Exception()

class PresenceSensorJsonPathMessageConversion(
    sensorIdPath: String, presencePath: String, timestampPath: String? = null
) {

    private val sensorIdExtractor = jsonPathExtractor<String>(sensorIdPath)
    private val presenceExtractor = jsonPathExtractorAny(presencePath)
    private val timestampExtractor = timestampPath?.let { jsonPathExtractor<Instant>(it) }

    operator fun invoke(sensorMessage: JsonMessage): PresenceChangeEvent {
        val messageBody: JsonObject = sensorMessage.payload

        val sensorId: String = sensorIdExtractor(messageBody) ?: throw MissingJsonFieldException("sensorId")
        val presenceValue = presenceExtractor(messageBody) ?: throw MissingJsonFieldException("presence")
        val timestamp: Instant = if (timestampExtractor != null) {
            timestampExtractor.invoke(messageBody) ?: throw MissingJsonFieldException("timestamp")
        } else {
            sensorMessage.timestamp
        }

        return PresenceChangeEvent(sensorId, Presence.parseValue(presenceValue), timestamp)
    }
}

fun sensorMessageAdapter(sensor: PresenceSensorEventDriven, messageConversion: (JsonMessage) -> PresenceChangeEvent): WebSocketMessageHandler {
    return { jsonMessage ->
        var event: PresenceChangeEvent? = null
        try {
            event = messageConversion(jsonMessage)
        } catch (e: MissingJsonFieldException) {
            MessageHandlerResult.Rejected("Missing JSON field ${e.field}")
        } catch (e: IllegalArgumentException) {
            MessageHandlerResult.Rejected(e.message ?: "Unknown reason")
        }

        if (event!!.sensorId != sensor.sensorId) {
            MessageHandlerResult.Rejected("Message not addressed for sensor ${sensor.sensorId}")
        }

        sensor.newUpdate(event)
        MessageHandlerResult.Accepted
    }
}

object PresenceSensors {

    fun sensord(
        sensorId: String,
        remoteMessaging: PresenceSensorRemoteMessaging
    ): PresenceSensorUpdatable {
        val sensor = PresenceSensorEventDriven(sensorId)
        val msgConversion = PresenceSensorJsonPathMessageConversion("sensorId", "eventData.presence", "eventAt")
        remoteMessaging.addPresenceSensorMessageHandler(sensorMessageAdapter(sensor, msgConversion::invoke))

        val updateRequestHandler = UpdateRequestBuilders.sensord().andThen(remoteMessaging::sendMessageToPresenceSensors)
        return PresenceSensorEventDrivenUpdatable(sensor, updateRequestHandler)
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
