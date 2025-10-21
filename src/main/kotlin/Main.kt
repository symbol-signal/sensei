package symsig.sensei

import de.kempmobil.ktor.mqtt.Disconnected
import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import symsig.sensei.ShellyPro2PMDimmer.Channel.Ch1
import symsig.sensei.ShellyPro2PMDimmer.Channel.Ch2
import symsig.sensei.devices.dimmer.KinconyD16Dimmer
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    runMqttApplication("central.local", 1883) { client ->
        val sunTimesService = SunTimesService(HttpClient(CIO))
        sunTimesService.updateTimes()
        launch {
            while (isActive) {
                delay(3.hours)
                sunTimesService.updateTimes()
            }
        }

        val dimmer = ShellyPro2PMDimmer(client, "shellyprodm2pm/rpc", this)
        val bathroomMainSensor = PresenceSensor(
            client, "home/bathroom/binary_sensor/bathroom_mmwave/state", this
        )
        val bathroomShowerSensor = PresenceSensor(
            client, "home/bathroom/binary_sensor/shower_presence/state", this, absentDelay = 6.seconds
        )
        val bathroomPresence = CombinedPresenceSensor(bathroomMainSensor, bathroomShowerSensor, scope = this)
        launch {
            val mirrorChannel = DelayableChannel(dimmer.channel(Ch1), this)
            val allChannels = CombinedChannel(dimmer.channel(Ch2), mirrorChannel)
            bathroomPresence.state.collect { state ->
                when (state) {
                    PresenceState.PRESENT -> {
                        dimmer.channel(Ch2).turnOn()
                        mirrorChannel.turnOn(3.seconds)
                    }
                    PresenceState.ABSENT, PresenceState.UNKNOWN -> allChannels.turnOff()
                }
            }
        }

        val bathroomFan = ShellyPlus1PMRelay(client, "shellyplus1pm-fan/rpc", this)
        val bathroomSwitch = Switch(client, "home/bathroom/switch/2/state", this)
        launch {
            bathroomSwitch.state
                .drop(1)
                .collect { state ->
                    if (state == SwitchState.ON) bathroomFan.toggle()
                }
        }

        val kinconyDimmer = KinconyD16Dimmer(
            client, "dimmer/d96c4bd0672e64279c34a168/set", this, mapOf(
                Channel.Ch1 to 17..45,
                Channel.Ch2 to 10..40,
                Channel.Ch3 to 20..32,
                Channel.Ch6 to 21..41,
            )
        )
        val hallwaySensor = PresenceSensor(
            client, "home/bathroom/binary_sensor/hallway_mmwave/state", this
        )
        launch {
            hallwaySensor.state.collect { state ->
                val hallwayChannel = if (sunTimesService.isNight()) Channel.Ch9 else Channel.Ch6
                when (state) {
                    PresenceState.PRESENT -> kinconyDimmer.channel(hallwayChannel).turnOn()
                    PresenceState.ABSENT, PresenceState.UNKNOWN -> kinconyDimmer.channel(hallwayChannel).turnOff()
                }
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