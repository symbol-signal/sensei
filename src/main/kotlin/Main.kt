package symsig.sensei

import de.kempmobil.ktor.mqtt.Disconnected
import de.kempmobil.ktor.mqtt.MqttClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime.now
import symsig.sensei.devices.CombinedPresenceSensor
import symsig.sensei.devices.PresenceSensor
import symsig.sensei.devices.PresenceState
import symsig.sensei.devices.Switch
import symsig.sensei.devices.SwitchState
import symsig.sensei.devices.dimmer.CombinedChannel
import symsig.sensei.devices.dimmer.DelayableChannel
import symsig.sensei.devices.dimmer.KinconyD16Dimmer
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch1
import symsig.sensei.devices.dimmer.ShellyPro2PMDimmer.Channel.Ch2
import symsig.sensei.devices.Switchable
import symsig.sensei.devices.relay.DelayableRelay
import symsig.sensei.devices.relay.KinconyMiniServerRelay
import symsig.sensei.devices.relay.ShellyPlus1PMRelay
import symsig.sensei.services.SolarService
import symsig.sensei.util.mqtt.MqttClientAdapter
import symsig.sensei.util.schedule.RollingScheduler
import symsig.sensei.util.time.schedules
import symsig.sensei.util.time.time
import symsig.sensei.util.time.window


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
//    runMqttApplication("central.local", 1883, runTest(solarService))
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
    val mqtt = MqttClientAdapter(client)
    val dayStart = solar.sunrise laterOf time("07:00")
    val dayEnd = solar.sunset laterOf time("18:00")

    val daytime = window(dayStart, dayEnd)
    val evening = window(dayEnd, "22:00")
    val windingDown = window("22:00", "23:00")
    val night = window("23:00", dayStart)

    val deskLight = Channel.Ch2
    val hallwayDayLight = Channel.Ch6
    val hallwayLedLight = Channel.Ch9
    val mainRoomLedLight = Channel.Ch8

    val dimmer = ShellyPro2PMDimmer(mqtt, "shellyprodm2pm/rpc", this)
    val bathroomMainSensor = PresenceSensor(
        mqtt, "home/bathroom/binary_sensor/bathroom_mmwave/state", this
    )
    val bathroomShowerSensor = PresenceSensor(
        mqtt, "home/bathroom/binary_sensor/shower_presence/state", this, absentDelay = 10.seconds
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

    val bathroomLightSwitch = Switch(mqtt, "home/bathroom/switch/1/state", this)
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

    val bathroomFan = ShellyPlus1PMRelay(mqtt, "shellyplus1pm-fan/rpc", this)
    val bathroomFanSwitch = Switch(mqtt, "home/bathroom/switch/2/state", this)
    launch {
        bathroomFanSwitch.state
            .drop(1)
            .collect { state ->
                if (state == SwitchState.ON) bathroomFan.toggle()
            }
    }
    launch {
        val delayedFan = DelayableRelay(bathroomFan, this, 45.seconds)
        bathroomShowerSensor.state.collect { state ->
            if (state == PresenceState.PRESENT) {
                delayedFan.turnOn()
            } else {
                delayedFan.cancel()
            }
        }
    }

    val kinconyDimmer = KinconyD16Dimmer(
        mqtt,
        "dimmer/d96c4bd0672e64279c34a168/state",
        "dimmer/d96c4bd0672e64279c34a168/set",
        this,
        mapOf(
            deskLight to 20..40,
            hallwayDayLight to 21..41,
        )
    )

    launch {
        RollingScheduler(
            schedules(
                solar.sunrise set 99,
                dayEnd to (dayEnd + 3.hours earlierOf time("22:00")) spread (99 downTo 30),
            ),
            action = { kinconyDimmer.channel(deskLight).setBrightness(it) }
        ).run()
    }

    launch {
        RollingScheduler(
            schedules(
                solar.sunrise set 99,
                time("21:00") to time("23:00") spread (99 downTo 60),
            ),
            action = { kinconyDimmer.channel(mainRoomLedLight).setBrightness(it) }
        ).run()
    }

    val hallwaySensor = PresenceSensor(
        mqtt, "home/bathroom/binary_sensor/hallway_mmwave/state", this, absentDelay = 2.seconds
    )
    launch {
        hallwaySensor.state.collect { state ->
            val (channel, brightness) = when (now()) {
                in daytime -> hallwayDayLight to 99
                in evening -> hallwayLedLight to 99
                in windingDown -> hallwayLedLight to 50
                else -> hallwayLedLight to 35
            }

            when (state) {
                PresenceState.PRESENT -> kinconyDimmer.channel(channel).turnOn(brightness)
                PresenceState.ABSENT, PresenceState.UNKNOWN -> kinconyDimmer.channels(hallwayDayLight, hallwayLedLight).turnOff()
            }
        }
    }

    val kitchenLight = KinconyMiniServerRelay(
        mqtt,
        stateTopic = "home/kitchen/light/state",
        commandTopic = "home/kitchen/light/command",
        this
    )
    val mainLight = KinconyMiniServerRelay(
        mqtt,
        stateTopic = "home/main/light/state",
        commandTopic = "home/main/light/command",
        this
    )

    val bedNightSwitch = Switch(mqtt, "home/bed/switch/1/state", this)
    launch {
        val lights: List<Switchable> = listOf(
            kinconyDimmer.channel(deskLight),
            kinconyDimmer.channel(mainRoomLedLight),
            kitchenLight,
            mainLight
        )
        var savedLights = listOf<Switchable>()
        bedNightSwitch.state
            .drop(1)
            .filter { it == SwitchState.OFF }
            .collect {
                val onNow = lights.filter { it.isOn.value }
                if (onNow.isNotEmpty()) {
                    onNow.forEach { it.turnOff() }
                    savedLights = onNow
                } else if (savedLights.isNotEmpty()) {
                    savedLights.forEach { it.turnOn() }
                }
            }
    }
}

//fun runTest(solar: SolarService): suspend CoroutineScope.(MqttClient) -> Unit = { client ->
//}