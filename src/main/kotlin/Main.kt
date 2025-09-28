package symsig.sensei

import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop

private val log = KotlinLogging.logger {}

fun main() {
    runMqttApplication("central.local", 1883) { client ->
        val bathroomSwitch = Switch(client, "home/bathroom/switch/2/state", this)

        launch {
            bathroomSwitch.state
                .drop(1)
                .collect { state ->
                    println("--> Received new state for bathroom switch: $state")
                }
        }
    }
}

fun runMqttApplication(host: String, port: Int, block: suspend CoroutineScope.(MqttClient) -> Unit) {
    val scope = CoroutineScope(
        SupervisorJob() +
                CoroutineExceptionHandler { _, throwable ->
                    log.error(throwable) { "unhandled_coroutine_exception" }
                }
    )

    val app = scope.launch {
        val client = MqttClient(host, port) {}
        val connAck = client.connect().getOrThrow()
        require(connAck.isSuccess) { "MQTT connect failed: $connAck" }
        log.info { "mqtt_connected" }

        try {
            coroutineScope { block(client) }
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
            app.cancelAndJoin()
        }
    })

    runBlocking { app.join() }
}