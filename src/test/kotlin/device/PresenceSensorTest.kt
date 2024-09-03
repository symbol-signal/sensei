package device

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import symsig.sensei.device.Presence
import symsig.sensei.device.PresenceChangeEvent
import symsig.sensei.device.PresenceSensor
import symsig.sensei.device.PresenceSensors
import symsig.sensei.`interface`.JsonMessage
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

class PresenceSensorTest : StringSpec({

    lateinit var fakeMessaging: PresenceSensorRemoteMessagingFake
    lateinit var sensor: PresenceSensor
    lateinit var presenceChangeEvents: MutableList<PresenceChangeEvent>
    lateinit var sensorHandler: (JsonMessage) -> Unit

    beforeTest {
        fakeMessaging = PresenceSensorRemoteMessagingFake()
        sensor = PresenceSensors.sensord("sen0395", fakeMessaging)
        presenceChangeEvents = mutableListOf()
        sensorHandler = fakeMessaging.presenceSensorHandlers[0]
        sensor.addListener { e -> presenceChangeEvents += e }
    }

    fun putSensorMessage(presence: String): Instant {
        val ts = now().truncatedTo(MILLIS)
        sensorHandler(jsonMsg {
            put("sensorId", "sen0395")
            putJsonObject("eventData") {
                put("presence", presence)
            }
            put("eventAt", ts.toEpochMilli())
        })
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
})
