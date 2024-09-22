package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import symsig.sensei.device.Presence
import symsig.sensei.device.PresenceSensors
import symsig.sensei.device.dimmer.ChildScopeDimmer
import symsig.sensei.device.dimmer.shelly.ShellyPro2PMDimmerHttp
import symsig.sensei.device.dimmer.shelly.ShellyPro2PMDimmerHttp.Companion.LIGHT_0
import symsig.sensei.device.dimmer.shelly.ShellyPro2PMDimmerHttp.Companion.LIGHT_1
import symsig.sensei.`interface`.WebSocketServer
import symsig.sensei.util.timer.DebounceScheduler
import java.time.Duration.ofSeconds
import java.time.LocalTime

private val log = KotlinLogging.logger {}

private val morningStart = LocalTime.of(6, 30)
private val eveningStart = LocalTime.of(18, 0)
private val nightStart = LocalTime.of(22, 0)
private val eveningToNight = eveningStart..nightStart

fun isBetweenNightMorning(): Boolean {
    val now = LocalTime.now()
    return now.isAfter(nightStart) || now.isBefore(morningStart)
}

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
                appScope.launch { bathroomDimmer.lights(LIGHT_1).lightOn() }

                val light0Delay: Long = if (isBetweenNightMorning()) 0 else 8
                debounceScheduler.schedule(ofSeconds(light0Delay)) { bathroomDimmer.lights(LIGHT_0).lightOn() }
            }
            Presence.ABSENT -> {
                appScope.launch { bathroomDimmer.lights(LIGHT_1).lightOff() }

                val light0Delay: Long = if (isBetweenNightMorning()) 0 else 3
                debounceScheduler.schedule(ofSeconds(light0Delay)) { bathroomDimmer.lights(LIGHT_0).lightOff() }
            }
            Presence.UNKNOWN -> log.warn { "[unknown_presence_state] sensor=[$bathroomSensor]" }
        }
    }

    bathroomDimmer.jobs.create().adjustBrightnessLinearly(eveningToNight, 100 downTo 10, LIGHT_0).start()
    bathroomDimmer.jobs.create().adjustBrightnessLinearly(eveningToNight, 100 downTo 0,  LIGHT_1).start()
    bathroomDimmer.jobs.create().setBrightnessDaily(morningStart, 100, LIGHT_0, LIGHT_1).start()

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
