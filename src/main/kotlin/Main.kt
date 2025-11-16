package symsig.sensei

import de.kempmobil.ktor.mqtt.Disconnected
import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import symsig.sensei.TimePeriod.DAYTIME
import symsig.sensei.TimePeriod.EVENING
import symsig.sensei.TimePeriod.NIGHTTIME
import symsig.sensei.TimePeriod.WINDING_DOWN
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch1
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch2
import symsig.sensei.devices.dimmer.KinconyD16Dimmer
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer
import symsig.sensei.services.SunTimesService
import java.time.LocalTime
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

    val sunTimesService = SunTimesService(HttpClient(CIO))
    launch {
        sunTimesService.runUpdate()
    }
    val dayCycle = DayCycle(sunTimesService, LocalTime.of(22, 0), LocalTime.of(23, 59))

    runMqttApplication("central.local", 1883, runRules(dayCycle))
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

fun runRules(dayCycle: DayCycle): suspend CoroutineScope.(MqttClient) -> Unit = { client ->
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
            val (channel, brightness) = when (dayCycle.getCurrentPeriod()) {
                DAYTIME      -> allChannels to 100
                EVENING      -> allChannels to dayCycle.evening.interpolate(80.0, 20.0).toInt()
                WINDING_DOWN -> allChannels to 20
                NIGHTTIME    -> dimmer.channel(Ch1) to 10
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
                    if (dayCycle.isNightTime()) {
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
    val hallwaySensor = PresenceSensor(
        client, "home/bathroom/binary_sensor/hallway_mmwave/state", this, absentDelay = 2.seconds
    )
    launch {
        hallwaySensor.state.collect { state ->
            val (channel, brightness) = when (dayCycle.getCurrentPeriod()) {
                DAYTIME      -> Channel.Ch6 to 99
                EVENING      -> Channel.Ch9 to 99
                WINDING_DOWN -> Channel.Ch9 to 50
                NIGHTTIME    -> Channel.Ch9 to 35
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