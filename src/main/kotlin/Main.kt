package symsig.sensei

import de.kempmobil.ktor.mqtt.Disconnected
import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import symsig.sensei.devices.dimmer.KinconyD16Dimmer
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch1
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch2
import symsig.sensei.services.SolarService
import symsig.sensei.util.schedule.RollingScheduler
import java.time.LocalDateTime.now
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() = runBlocking {
    val job = coroutineContext.job

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "shutdown_sequence_executed" }
        runBlocking {
            job.cancelAndJoin()
        }
    })

    val solarService = SolarService(HttpClient(CIO))
    solarService.update()
    launch {
        solarService.runUpdate()
    }

    runMqttApplication("central.local", 1883, runRules(solarService))
//    runMqttApplication("central.local", 1883, runTest(sunTimesService))
    log.info { "application_finished_gracefully" }
}


suspend fun runMqttApplication(host: String, port: Int, block: suspend CoroutineScope.(MqttClient) -> Unit) {
    var reconnecting = false
    while (currentCoroutineContext().isActive) {
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
            if (!reconnecting) {
                log.info { "mqtt_client_closed" }
            }
        }

        if (!reconnecting) {
            log.info { "mqtt_reconnecting" }
        }
        delay(10.seconds)
        reconnecting = true
    }
}

fun runRules(solar: SolarService): suspend CoroutineScope.(MqttClient) -> Unit = { client ->
    val daytime = window(solar.sunrise, solar.sunset)
    val evening = window(solar.sunset, "22:00")
    val windingDown = window("22:00", "23:00")
    val night = window("23:00", solar.sunrise)

    val dimmer = ShellyPro2PMDimmer(client, "shellyprodm2pm/rpc", this)
    val bathroomMainSensor = PresenceSensor(
        client, "home/bathroom/binary_sensor/bathroom_mmwave/state", this
    )
    val bathroomShowerSensor = PresenceSensor(
        client, "home/bathroom/binary_sensor/shower_presence/state", this, absentDelay = 10.seconds
    )
    val bathroomPresence = CombinedPresenceSensor(bathroomMainSensor, bathroomShowerSensor, scope = this)
    val delayedMirrorChannel = DelayableChannel(dimmer.channel(Ch1), this, 3.seconds)
    launch {
        val allChannels = CombinedChannel(dimmer.channel(Ch2), delayedMirrorChannel)
        bathroomPresence.state.collect { state ->
            val (channel, brightness) = when (val now = now()) {
                in daytime -> allChannels to 100
                in evening -> allChannels to evening.interpolate(now, 80.0, 20.0).toInt()
                in windingDown -> dimmer.channel(Ch1) to 30
                else -> dimmer.channel(Ch1) to 10
            }
            when (state) {
                PresenceState.PRESENT -> channel.turnOn(brightness)
                PresenceState.ABSENT, PresenceState.UNKNOWN -> allChannels.turnOff()
            }
        }
    }

    val bathroomLightSwitch = Switch(client, "home/bathroom/switch/1/state", this)
    launch {
        bathroomLightSwitch.state
            .drop(1)
            .collect { state ->
                if (state == SwitchState.ON) {
                    if (now() in night) {
                        dimmer.channel(Ch1).toggle()
                    } else {
                        dimmer.channel(Ch1).toggle()
                        dimmer.channel(Ch2).toggle()
                    }
                }
            }
    }

    val bathroomFan = ShellyPlus1PMRelay(client, "shellyplus1pm-fan/rpc", this)
    val bathroomFanSwitch = Switch(client, "home/bathroom/switch/2/state", this)
    launch {
        bathroomFanSwitch.state
            .drop(1)
            .collect { state ->
                if (state == SwitchState.ON) bathroomFan.toggle()
            }
    }

    val kinconyDimmer = KinconyD16Dimmer(
        client,
        "dimmer/d96c4bd0672e64279c34a168/state",
        "dimmer/d96c4bd0672e64279c34a168/set",
        this,
        mapOf(
            Channel.Ch1 to 17..45,
            Channel.Ch2 to 10..40,
            Channel.Ch3 to 20..32,
            Channel.Ch6 to 21..41,
        )
    )

    launch {
        RollingScheduler(
            schedules(
                solar.sunrise set 99,
                (solar.sunset to solar.sunset + 3.hours) spread (99 downTo 30),
            ),
            action = { kinconyDimmer.channel(Channel.Ch2).setBrightness(it) }
        ).run()
    }

    val hallwaySensor = PresenceSensor(
        client, "home/bathroom/binary_sensor/hallway_mmwave/state", this, absentDelay = 2.seconds
    )
    launch {
        hallwaySensor.state.collect { state ->
            val (channel, brightness) = when (now()) {
                in daytime -> Channel.Ch6 to 99
                in evening -> Channel.Ch9 to 99
                in windingDown -> Channel.Ch9 to 50
                else -> Channel.Ch9 to 35
            }

            when (state) {
                PresenceState.PRESENT -> kinconyDimmer.channel(channel).turnOn(brightness)
                PresenceState.ABSENT, PresenceState.UNKNOWN -> kinconyDimmer.channels(Channel.Ch6, Channel.Ch9).turnOff()
            }
        }
    }
}

//fun runTest(sunTimesService: SunTimesService): suspend CoroutineScope.(MqttClient) -> Unit = { client ->
//}