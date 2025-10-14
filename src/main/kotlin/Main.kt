package symsig.sensei

import de.kempmobil.ktor.mqtt.Disconnected
import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    runMqttApplication("central.local", 1883) { client ->
        val bathroomSwitch = Switch(client, "home/bathroom/switch/2/state", this)
        val bathroomFan = ShellyPlus1PM(client, "shellyplus1pm-fan/rpc", this)

        launch {
            bathroomSwitch.state
                .drop(1)
                .collect { state ->
                    if (state == State.ON) bathroomFan.toggle()
                }
        }
    }
}

fun runMqttApplication(host: String, port: Int, block: suspend CoroutineScope.(MqttClient) -> Unit) {
    runBlocking {
        val job = coroutineContext.job

        Runtime.getRuntime().addShutdownHook(Thread {
            log.info { "shutdown_sequence_executed" }
            runBlocking {
                job.cancelAndJoin()
            }
        })

        var reconnecting = false
        while (isActive) {
            val client = MqttClient(host, port) {}

            try {
                val result = client.connect()
                if (result.isSuccess && result.getOrNull()?.isSuccess == true) {
                    log.info { "mqtt_connected" }

                    coroutineScope {
                        launch { block(client) }
                        reconnecting = false
                        client.connectionState.first { it is Disconnected }  // Wait for disconnection

                        log.warn { "mqtt_disconnected" }
                        coroutineContext.cancelChildren()
                    }
                } else {
                    if (!reconnecting) {
                        log.error { "mqtt_connect_failed reason=[${result.exceptionOrNull() ?: result.getOrNull()?.reason}]" }
                    }
                }
            } catch (_: CancellationException) {
                break
            } finally {
                client.close()
                if (!reconnecting) { log.info { "mqtt_client_closed" } }
            }

            if (!reconnecting) { log.info { "mqtt_reconnecting" } }
            delay(10.seconds)
            reconnecting = true
        }
    }
}