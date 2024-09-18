package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import symsig.sensei.device.ChildScopeDimmer
import symsig.sensei.device.Presence
import symsig.sensei.device.PresenceSensors
import symsig.sensei.device.ShellyPro2PMDimmerHttp
import symsig.sensei.`interface`.WebSocketServer
import symsig.sensei.util.timer.DebounceScheduler
import java.time.Duration.ofSeconds
import java.time.LocalTime

private val log = KotlinLogging.logger {}

private val wakeupTime = LocalTime.of(6, 30)
private val eveningToNight = LocalTime.of(18, 0)..LocalTime.of(22, 0)

fun main() {
    val appScope = createAppCoroutineScope()

    val wsServer = WebSocketServer(8080)

    val bathroomSensor = PresenceSensors.sensord("sen0395/bathroom", wsServer)
    bathroomSensor.addListener { event ->
        log.info { "[sensor_event] sensorId=[${event.sensorId}], presence=[${event.presence}], changedAt=[${event.changedAt}]" }
    }

    val httpClient = HttpClient(CIO)
    val bathroomDimmer = ChildScopeDimmer(appScope, ShellyPro2PMDimmerHttp("shellyprodm2pm-08f9e0e49950", httpClient))
    val debounceScheduler = DebounceScheduler(appScope)
    bathroomSensor.addListener { event ->
        when (event.presence) {
            Presence.PRESENT -> {
                appScope.launch { bathroomDimmer.lightOn("1") }
                debounceScheduler.schedule(ofSeconds(5)) { bathroomDimmer.lightOn("0") }
            }
            Presence.ABSENT -> {
                appScope.launch { bathroomDimmer.lightOff("1") }
                debounceScheduler.schedule(ofSeconds(3)) { bathroomDimmer.lightOff("0") }
            }
            Presence.UNKNOWN -> log.warn { "[unknown_presence_state] sensor=[$bathroomSensor]" }
        }
        appScope.launch {
            when (event.presence) {
                Presence.PRESENT -> bathroomDimmer.lightOn("1")
                Presence.ABSENT -> bathroomDimmer.lightOff("1")
                Presence.UNKNOWN -> log.warn { "[unknown_presence_state] sensor=[$bathroomSensor]" }
            }
        }

    }

    bathroomDimmer.jobs.create().setBrightnessDaily(wakeupTime, 100, "1").start()
    bathroomDimmer.jobs.create().adjustBrightnessLinearly(eveningToNight, 100 downTo 15, "1").start()

    wsServer.start()
    log.info { "[ws_server_started]" }

    setupShutdownSequence(appScope, wsServer)

    log.info { "[app_initialized]" }
}

private fun createAppCoroutineScope(): CoroutineScope {
    val excHandler = CoroutineExceptionHandler { _, throwable ->
        log.error(throwable) { "[unhandled_coroutine_exception]" }
    }
    return CoroutineScope(SupervisorJob() + excHandler)
}

private fun setupShutdownSequence(appScope: CoroutineScope, wsServer: WebSocketServer) {
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "[shutdown_initiated]" }
        log.info { "[stopping_ws_server]" }
        appScope.cancel()
        wsServer.stop(1000, 1000)
    })
}
