package device

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.*
import symsig.sensei.device.Presence
import symsig.sensei.device.PresenceChangeEvent
import symsig.sensei.device.PresenceSensor
import symsig.sensei.device.PresenceSensors
import symsig.sensei.`interface`.JsonMessage
import symsig.sensei.`interface`.MessageHandlerResult.Accepted
import symsig.sensei.`interface`.MessageHandlerResult.Rejected
import symsig.sensei.`interface`.PresenceSensorRemoteMessaging
import symsig.sensei.`interface`.WebSocketMessageHandler
import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit.MILLIS

class PresenceSensorRemoteMessagingFake : PresenceSensorRemoteMessaging {

    private val messagesSent = mutableListOf<JsonObject>()
    internal val presenceSensorHandlers = mutableListOf<WebSocketMessageHandler>()

    override fun sendMessageToPresenceSensors(message: JsonObject) {
        messagesSent += message
    }

    override fun addPresenceSensorMessageHandler(handler: WebSocketMessageHandler) {
        presenceSensorHandlers += handler
    }

    override fun removePresenceSensorMessageHandler(handler: WebSocketMessageHandler) {
        presenceSensorHandlers -= handler
    }
}

fun jsonMsg(builder: JsonObjectBuilder.() -> Unit): JsonMessage {
    return JsonMessage(buildJsonObject(builder))
}

private const val SENSOR_ID = "sen0395"

class PresenceSensorTest : StringSpec({

    lateinit var fakeMessaging: PresenceSensorRemoteMessagingFake
    lateinit var sensor: PresenceSensor
    lateinit var presenceChangeEvents: MutableList<PresenceChangeEvent>
    lateinit var sensorHandler: WebSocketMessageHandler

    beforeTest {
        fakeMessaging = PresenceSensorRemoteMessagingFake()
        sensor = PresenceSensors.sensord(SENSOR_ID, fakeMessaging)
        sensorHandler = fakeMessaging.presenceSensorHandlers[0]
        presenceChangeEvents = mutableListOf()
        sensor.addListener { e -> presenceChangeEvents += e }
    }

    fun lastEvent(): PresenceChangeEvent {
        return presenceChangeEvents.last()
    }

    fun putSensorMessage(presence: String, sensorId: String = SENSOR_ID, shouldBeAccepted: Boolean = true): Instant {
        val ts = now().truncatedTo(MILLIS)
        val res = sensorHandler(jsonMsg {
            put("sensorId", sensorId)
            putJsonObject("eventData") {
                put("presence", presence)
            }
            put("eventAt", ts.toEpochMilli())
        })
        if (shouldBeAccepted) res.shouldBeInstanceOf<Accepted>() else res.shouldBeInstanceOf<Rejected>()
        return ts
    }

    "test sensor state" {
        sensor.presence shouldBe Presence.UNKNOWN

        var ts = putSensorMessage("off")
        sensor.presence shouldBe Presence.ABSENT
        sensor.lastChanged shouldBe ts

        ts = putSensorMessage("on")
        sensor.presence shouldBe Presence.PRESENT
        sensor.lastChanged shouldBe ts

        putSensorMessage("on")
        sensor.presence shouldBe Presence.PRESENT
        sensor.lastChanged shouldBe ts  // Timestamp should not change as the state did not change

        ts = putSensorMessage("unknown")
        sensor.presence shouldBe Presence.UNKNOWN
        sensor.lastChanged shouldBe ts
    }

    "sensor is updated only when it is targeted" {
        val ts = putSensorMessage("on")
        sensor.presence shouldBe Presence.PRESENT
        sensor.lastChanged shouldBe ts

        putSensorMessage("off", sensorId = "different sensor", shouldBeAccepted = false)
        sensor.presence shouldBe Presence.PRESENT // No change
        sensor.lastChanged shouldBe ts // No change
    }

    "message timestamp is mandatory" {
        val ts = putSensorMessage("on")
        sensor.presence shouldBe Presence.PRESENT
        sensor.lastChanged shouldBe ts

        sensorHandler(jsonMsg {
            put("sensorId", SENSOR_ID)
            putJsonObject("eventData") {
                put("presence", 0)
            }
        }).shouldBeInstanceOf<Rejected>()

        sensor.presence shouldBe Presence.PRESENT // No change
        sensor.lastChanged shouldBe ts // No change
    }

    "event listeners are notified" {
        val ts = putSensorMessage("off")
        val e1 = lastEvent()
        e1.sensorId shouldBe SENSOR_ID
        e1.presence shouldBe Presence.ABSENT
        e1.changedAt shouldBe ts

        putSensorMessage("on", sensorId = "different", shouldBeAccepted = false)
        e1 shouldBe lastEvent()

        putSensorMessage("on")
        e1 shouldNotBe lastEvent()
    }
})
