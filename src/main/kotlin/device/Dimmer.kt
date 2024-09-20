package symsig.sensei.device

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import symsig.sensei.util.timer.DailyTimer
import symsig.sensei.util.timer.LinearSequenceTimer
import symsig.sensei.util.timer.SequenceUpdate
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface for controlling a dimmer.
 */
interface Light {

    /**
     * Turns on the light
     *
     * @throws RemoteOpException If an error occurs while sending the command.
     */
    suspend fun lightOn()

    /**
     * Turns off the light
     *
     * @throws RemoteOpException If an error occurs while sending the command.
     */
    suspend fun lightOff()

    /**
     * Set brightness level (usually between 0 and 100)
     *
     * @throws RemoteOpException If an error occurs while sending the command.
     */
    suspend fun setBrightness(value: Int)
}

/**
 * Interface for controlling a dimmer.
 */
interface Dimmer : Light {

    fun lights(vararg lightIds: String): Light
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

    private val _active = CopyOnWriteArrayList<DimmerJob>()

    val active: List<DimmerJob> get() = _active.toList()

    fun create() = factory

    abstract inner class DimmerJob(val jobName: String) {

        private var job: Job? = null
        val isActive get() = job?.isActive == true

        protected abstract suspend fun execute()

        fun start() {
            check(!isActive) { "Job $jobName is already running" }

            job = scope.launch(CoroutineName(jobName)) {
                try {
                    execute()
                } finally {
                    job = null
                    _active.remove(this@DimmerJob)
                }
            }
            _active.add(this)
            log.info { "[dimmer_job_started] job_name=[$jobName]" }
        }

        fun cancel() {
            val currentJob = job
            check(currentJob != null && currentJob.isActive) { "No active $jobName job to cancel" }

            currentJob.cancel()

            log.info { "[dimmer_job_cancelled] job_name=[$jobName]" }
        }
    }

    inner class DimmerJobsFactory {

        fun setBrightnessDaily(at: LocalTime, brightnessValue: Int, vararg lightIds: String): SetBrightnessDailyJob {
            return SetBrightnessDailyJob(at, brightnessValue, lightIds.toSet())
        }

        fun adjustBrightnessLinearly(
            between: ClosedRange<LocalTime>,
            brightness: IntProgression,
            vararg lightIds: String
        ): LinearBrightnessAdjustmentJob {
            return LinearBrightnessAdjustmentJob(between, brightness, lightIds.toSet())
        }
    }

    inner class SetBrightnessDailyJob(
        val at: LocalTime,
        val brightnessValue: Int,
        private val lightIds: Set<String> = emptySet()
    ) : DimmerJob("set_brightness_daily") {

        override suspend fun execute() {
            DailyTimer(at, this::adjustBrightness).run()
        }

        private suspend fun adjustBrightness() {
            try {
                dimmer.lights(*lightIds.toTypedArray()).setBrightness(brightnessValue)
                log.info { "[dimmer_brightness_set] value=[$brightnessValue]" }
            } catch (e: RemoteOpException) {
                log.error(e) { "[scheduled_dimmer_brightness_adjustment_failed]" }
            }
        }
    }

    inner class LinearBrightnessAdjustmentJob(
        private val between: ClosedRange<LocalTime>,
        private val brightness: IntProgression,
        private val lightIds: Set<String> = emptySet()
    ) : DimmerJob("linear_brightness_adjustment") {

        override suspend fun execute() {
            LinearSequenceTimer(between, brightness, this::adjustBrightness).run()
        }

        private suspend fun adjustBrightness(update: SequenceUpdate) {
            try {
                dimmer.lights(*lightIds.toTypedArray()).setBrightness(update.value)
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
class ShellyPro2PMDimmerHttp(
    private val hostname: String,
    private val client: HttpClient
) : Dimmer {

    companion object {
        const val LIGHT_0 = "0"
        const val LIGHT_1 = "1"
        val ALL_CHANNELS = setOf(LIGHT_0, LIGHT_1)
    }

    /**
     * Returns a [Light] instance controlling the specified [lightIds].
     */
    override fun lights(vararg lightIds: String): Light {
        return ShellyLight(lightIds.toSet())
    }

    override suspend fun lightOn() {
        ShellyLight(ALL_CHANNELS).lightOn()
    }

    override suspend fun lightOff() {
        ShellyLight(ALL_CHANNELS).lightOff()
    }

    override suspend fun setBrightness(value: Int) {
        ShellyLight(ALL_CHANNELS).setBrightness(value)
    }

    /**
     * Inner class implementing [Light], controlling specific light IDs.
     */
    inner class ShellyLight(private val lightIds: Set<String>) : Light {

        /**
         * Turns on the specified lights.
         *
         * @throws RemoteOpException If an error occurs while sending the command.
         */
        override suspend fun lightOn() {
            for (lightId in lightIds) {
                sendSwitchCommand(lightId, true)
            }
        }

        /**
         * Turns off the specified lights.
         *
         * @throws RemoteOpException If an error occurs while sending the command.
         */
        override suspend fun lightOff() {
            for (lightId in lightIds) {
                sendSwitchCommand(lightId, false)
            }
        }

        /**
         * Sets the brightness for the specified lights.
         *
         * @param value The brightness value to set (0-100).
         * @throws IllegalArgumentException If the brightness value is out of range.
         * @throws RemoteOpException If an error occurs while sending the command.
         */
        override suspend fun setBrightness(value: Int) {
            require(value in 0..100) { "Brightness value $value is not between 0 and 100" }
            for (lightId in lightIds) {
                setBrightnessForLight(lightId, value)
            }
        }
    }

    /**
     * Sends the command to turn the light on or off.
     *
     * @param lightId The ID of the light.
     * @param on Whether to turn the light on (true) or off (false).
     * @throws RemoteOpException If the HTTP request fails or the response indicates an error.
     */
    private suspend fun sendSwitchCommand(lightId: String, on: Boolean) {
        val url = "http://$hostname/rpc/Light.Set?id=$lightId&on=$on"
        val response: HttpResponse = client.get(url)

        if (response.status.value !in 200..299) {
            throw RemoteOpException("Failed to send switch command to light $lightId: HTTP ${response.status.value}")
        }
    }

    /**
     * Sets the brightness for a specific light.
     *
     * @param lightId The ID of the light.
     * @param value The brightness value to set (0-100).
     * @throws RemoteOpException If the HTTP request fails or the response indicates an error.
     */
    private suspend fun setBrightnessForLight(lightId: String, value: Int) {
        val url = "http://$hostname/rpc/Light.Set?id=$lightId&brightness=$value"
        val response: HttpResponse = client.get(url)

        if (response.status.value !in 200..299) {
            throw RemoteOpException("Failed to set brightness for light $lightId: HTTP ${response.status.value}")
        }
    }
}
