package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import symsig.sensei.util.timer.LinearSequenceTimer
import symsig.sensei.util.timer.SequenceUpdate
import java.time.LocalTime

/**
 * Interface for controlling a dimmer.
 */
interface Dimmer {

    /**
     * Turns on the light with the given [lightId].
     *
     * @param lightId The ID of the light to turn on.
     * @throws Exception If an error occurs while sending the command.
     */
    suspend fun lightOn(lightId: String)

    /**
     * Turns off the light with the given [lightId].
     *
     * @param lightId The ID of the light to turn off.
     * @throws Exception If an error occurs while sending the command.
     */
    suspend fun lightOff(lightId: String)

    suspend fun setBrightness(lightId: String, value: Int)
}

interface ManagedDimmer : Dimmer {

    val jobs: DimmerJobs

    fun cancel()
}

open class ChildScopeDimmer(parentScope: CoroutineScope, dimmer: Dimmer) : Dimmer by dimmer, ManagedDimmer {

    protected val job = SupervisorJob(parentScope.coroutineContext[Job])
    protected val scope = parentScope + job

    override val jobs = DimmerJobs(scope, dimmer)

    override fun cancel() {
        job.cancel()
    }
}

class DimmerJobs(private val scope: CoroutineScope, private val dimmer: Dimmer) {

    private val log = KotlinLogging.logger {}

    private val factory = DimmerJobsFactory()

    fun create() = factory

    inner class DimmerJobsFactory {

        fun adjustBrightnessLinearly(lightId: String, between: ClosedRange<LocalTime>, brightness: IntProgression): LinearBrightnessAdjustmentJob {
            return LinearBrightnessAdjustmentJob(between, brightness, setOf(lightId))
        }
    }

    inner class LinearBrightnessAdjustmentJob(private val between: ClosedRange<LocalTime>, private val brightness: IntProgression, private val lightIds: Set<String> = emptySet()) {

        private var job: Job? = null
        private val isActive get() = job?.isActive == true

        fun start() {
            check(!isActive) { "Linear brightness adjustment job is already running" }

            val timer = LinearSequenceTimer(between, brightness, this::adjustBrightness)
            job = scope.launch {
                try {
                    timer.run()
                } finally {
                    job = null
                }
            }
            log.info { "[linear_brightness_adjustment_job_started]" }
        }

        fun cancel() {
            val currentJob = job
            check(currentJob != null && currentJob.isActive) { "No active linear brightness adjustment job to cancel" }

            currentJob.cancel()
            log.info { "[linear_brightness_adjustment_job_cancelled]" }
        }

        private suspend fun adjustBrightness(update: SequenceUpdate) {
            try {
                lightIds.forEach { dimmer.setBrightness(it, update.value) }
                log.info { "[dimmer_brightness_set] value=[${update.value}]" }
            } catch (e: RemoteOpException) {
                log.error(e) { "[scheduled_dimmer_brightness_adjustment_failed]" }
            }
        }
    }
}


/**
 * Implementation of [Dimmer] for Shelly Pro 2PM dimmer via HTTP control.
 *
 * @property hostname The hostname or IP address of the Shelly Pro 2PM device.
 */
class ShellyPro2PMDimmerHttp(private val hostname: String, private val client: HttpClient) : Dimmer {

    /**
     * Turns on the light with the given [lightId].
     *
     * @param lightId The ID of the light to turn on.
     * @throws Exception If an error occurs while sending the command to the dimmer.
     */
    override suspend fun lightOn(lightId: String) {
        sendSwitchCommand(lightId, true)
    }

    /**
     * Turns off the light with the given [lightId].
     *
     * @param lightId The ID of the light to turn off.
     * @throws Exception If an error occurs while sending the command to the dimmer.
     */
    override suspend fun lightOff(lightId: String) {
        sendSwitchCommand(lightId, false)
    }

    /**
     * Sets the brightness of the light.
     *
     * @param value The brightness value to set (0-100).
     * @throws IllegalArgumentException If the brightness value is out of range.
     * @throws Exception If an error occurs while sending the command to the dimmer.
     */
    override suspend fun setBrightness(lightId: String, value: Int) {
        require (value in 0..100) { "Brightness value $value is not between 0 and 100" }

        val url = "http://$hostname/rpc/Light.Set?id=$lightId&brightness=$value"
        val response: HttpResponse = client.get(url)

        if (response.status.value !in 200..299) {
            throw RemoteOpException("Failed to set brightness: HTTP ${response.status.value}")
        }
    }

    /**
     * Sends the command to turn the light on or off.
     *
     * @param lightId The ID of the light.
     * @param on Whether to turn the light on (true) or off (false).
     * @throws Exception If the HTTP request fails or the response indicates an error.
     */
    private suspend fun sendSwitchCommand(lightId: String, on: Boolean) {
        val url = "http://$hostname/rpc/Light.Set?id=$lightId&on=$on"
        val response: HttpResponse = client.get(url)

        if (response.status.value !in 200..299) {
            throw RemoteOpException("Failed to send switch command to light $lightId: HTTP ${response.status.value}")
        }
    }
}
