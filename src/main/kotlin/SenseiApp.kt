package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import symsig.sensei.device.PresenceSensors
import symsig.sensei.`interface`.WebSocketServer

private val log = KotlinLogging.logger {}


fun main() {
    val wsServer = WebSocketServer(8080)
    PresenceSensors.sensord("sen0395/bathroom", wsServer)
    wsServer.start()
    log.info { "[ws_server_started]" }

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutdown hook triggered")
        runBlocking {
            log.info { "[stopping_ws_server]" }
            wsServer.stop(1000, 1000)
        }
    })

    log.info { "[app_initialized]" }
}
