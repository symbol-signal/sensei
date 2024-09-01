package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import symsig.sensei.device.Presence
import symsig.sensei.device.PresenceSensors
import symsig.sensei.device.ShellyPro2PMDimmerHttp
import symsig.sensei.`interface`.WebSocketServer

private val log = KotlinLogging.logger {}

fun main() {
    val wsServer = WebSocketServer(8080)

    val bathroomSensor = PresenceSensors.sensord("sen0395/bathroom", wsServer)
    bathroomSensor.addListener { event ->
        log.info { "[sensor_event] sensorId=[${event.sensorId}], presence=[${event.presence}], changedAt=[${event.changedAt}]" }
    }

    val bathroomDimmer = ShellyPro2PMDimmerHttp("shellyprodm2pm-08f9e0e49950")
    bathroomSensor.addListener { event ->
        when (event.presence) {
            Presence.PRESENT -> bathroomDimmer.lightOn("1")
            Presence.ABSENT -> bathroomDimmer.lightOff("1")
            Presence.UNKNOWN -> log.warn { "[unknown_presence_state] sensor=[$bathroomSensor]" }
        }
    }

    wsServer.start()
    log.info { "[ws_server_started]" }

    setupShutdownSequence(wsServer)

    log.info { "[app_initialized]" }
}

private fun setupShutdownSequence(wsServer: WebSocketServer) {
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutdown hook triggered")
        runBlocking {
            log.info { "[stopping_ws_server]" }
            wsServer.stop(1000, 1000)
        }
    })
}
