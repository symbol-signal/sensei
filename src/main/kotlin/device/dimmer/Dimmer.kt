package symsig.sensei.device.dimmer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import symsig.sensei.device.RemoteOpException
import symsig.sensei.util.timer.DailyTimer
import symsig.sensei.util.timer.LinearSequenceTimer
import symsig.sensei.util.timer.SequenceUpdate
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.toKotlinDuration

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
class DimmerJobs<D : Dimmer>(
    private val scope: CoroutineScope,
    private val dimmer: D
) {
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

    inner class CustomDimmerJob(
        private val interval: Duration,
        private val action: suspend (D) -> Unit,
        customJobName: String? = null
    ) : DimmerJob(customJobName ?: "custom_scheduled_job") {

        override suspend fun execute() {
            while (isActive) {
                try {
                    action(dimmer)
                    delay(interval.toKotlinDuration())
                } catch (e: Exception) {
                    log.error(e) { "[custom_dimmer_job_failed] job_name=[${jobName}]" }
                }
            }
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

        fun schedule(
            interval: Duration,
            jobName: String? = null,
            action: suspend (D) -> Unit
        ): CustomDimmerJob {
            return CustomDimmerJob(interval, action, jobName)
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

// Update the ManagedDimmer interface to be generic
interface ManagedDimmer<D : Dimmer> : Dimmer {
    val jobs: DimmerJobs<D>
    fun cancel()
}

// Update ChildScopeDimmer to be generic
open class ChildScopeDimmer<D : Dimmer>(
    parentScope: CoroutineScope,
    dimmer: D
) : Dimmer by dimmer, ManagedDimmer<D> {
    protected val job = SupervisorJob(parentScope.coroutineContext[Job])
    protected val scope = parentScope + job

    override val jobs = DimmerJobs(scope, dimmer)

    override fun cancel() {
        job.cancel()
    }
}