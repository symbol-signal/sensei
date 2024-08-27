package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.util.json.jsonPathExtractor
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val log = KotlinLogging.logger {}

data class PresenceChangeEvent(
    val sensorId: String,
    val presence: Boolean,
    val changedAt: Instant,
)

class UpdateRequestNotAvailableException(sensorId: String) :
    Exception("Sensor $sensorId does not support requesting an update")

interface PresenceSensor {

    val sensorId: String
    val presence: Boolean? // TODO should be enum or sealed class?
    val lastChanged: Instant?

    fun addListener(listener: (PresenceChangeEvent) -> Unit)
    fun removeListener(listener: (PresenceChangeEvent) -> Unit)

    /**
     * Requests an update from the sensor.
     *
     * @throws UpdateRequestNotAvailableException if the sensor cannot handle update requests.
     */
    fun requestUpdate()
}

abstract class AbstractPresenceSensor : PresenceSensor {

    override var presence: Boolean? = null
        protected set

    override var lastChanged: Instant? = null
        protected set

    private val listeners = CopyOnWriteArrayList<(PresenceChangeEvent) -> Unit>()

    abstract override fun requestUpdate()

    fun newUpdate(newPresence: Boolean, updatedAt: Instant) {
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
    val presence: Boolean,
    val changedAt: Instant,
)

class MissingPresenceEventJsonFieldException(var sensorId: String, var field: String) : Exception()

class PresenceSensorJsonPathMessageProcessor(
    sensorIdPath: String,
    presencePath: String,
    timestampPath: String? = null
) {

    private var sensorIdExtractor = jsonPathExtractor<String>(sensorIdPath)
    private var presenceExtractor = jsonPathExtractor<Boolean>(presencePath)
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

        return PresenceSensorMessage(sensorId, presenceValue, timestamp)
    }
}

class PresenceSensorJson(override var sensorId: String, private var messageProcessor: (JsonMessage) -> PresenceSensorMessage?) :
    AbstractPresenceSensor() {

    fun handleSensorJsonMessage(jsonMessage: JsonMessage) {
        val presenceSensorMessage: PresenceSensorMessage?

        try {
            presenceSensorMessage = messageProcessor(jsonMessage) ?: return
        } catch (e: MissingPresenceEventJsonFieldException) {
            if (e.sensorId == sensorId) {
                // Implement logging or handle the exception
                TODO("Implement logging")
            }
            return
        }

        if (presenceSensorMessage.sensorId != sensorId) return

        newUpdate(presenceSensorMessage.presence, presenceSensorMessage.changedAt)
    }

    override fun requestUpdate() {
        TODO("Not yet implemented")
    }
}


object PresenceSensorJsonMessageProcessors {

    var sensord = PresenceSensorJsonPathMessageProcessor("sensorId", "eventData.presence", "eventAt")
}
