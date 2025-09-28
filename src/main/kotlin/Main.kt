package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop

private val log = KotlinLogging.logger {}

fun main() {
    val client = MqttClient("central.local", 1883) { /* auth/logging if you need */ }
    val appScope = createAppCoroutineScope()

    val application = appScope.launch {
        val connAck = client.connect().getOrThrow()
        require(connAck.isSuccess) { "MQTT connect failed: $connAck" }
        log.info { "mqtt_connected" }

        try {
            coroutineScope { // THIS waits for all children to complete
                val bathroomSwitch = Switch(client, "home/bathroom/switch/2/state", this)
                launch {
                    bathroomSwitch.state
                        .drop(1)
                        .collect { state ->
                            println("--> Received new state for bathroom switch: $state")
                        }
                }
            }
        } finally {
            withContext(NonCancellable) {
                client.disconnect()
                log.info { "mqtt_disconnected" }
            }
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "shutdown_sequence_executed" }
        runBlocking {
            application.cancel()
            application.join()
        }
    })

    runBlocking { application.join() }
}

private fun createAppCoroutineScope(): CoroutineScope {
    val excHandler = CoroutineExceptionHandler { _, throwable ->
        log.error(throwable) { "unhandled_coroutine_exception" }
    }
    return CoroutineScope(SupervisorJob() + excHandler)
}