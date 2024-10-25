package symsig.sensei.device.dimmer.kincony

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import symsig.sensei.device.RemoteOpException
import symsig.sensei.device.dimmer.Dimmer
import symsig.sensei.device.dimmer.Light
import symsig.sensei.device.dimmer.shelly.ShellyPro2PMDimmerHttp

private val DEFAULT_RANGE = 0..100

class Kincony16ChannelDimmer(
    private val hostname: String,
    private val password: String,
    private val client: HttpClient,
    private val workingRanges: Map<String, IntRange> = mapOf(),
) : Dimmer {
    
    private val channelToBrightness = mutableMapOf<String, Int>()

    companion object {
        const val DIMMER_01 = "01"
        const val DIMMER_02 = "02"
        const val DIMMER_03 = "03"
        const val DIMMER_04 = "04"
        const val DIMMER_05 = "05"
        const val DIMMER_06 = "06"
        const val DIMMER_07 = "07"
        const val DIMMER_08 = "08"
        const val DIMMER_09 = "09"
        const val DIMMER_10 = "10"
        const val DIMMER_11 = "11"
        const val DIMMER_12 = "12"
        const val DIMMER_13 = "13"
        const val DIMMER_14 = "14"
        const val DIMMER_15 = "15"
        const val DIMMER_16 = "16"

        val ALL_CHANNELS = setOf(
            DIMMER_01, DIMMER_02, DIMMER_03, DIMMER_04, DIMMER_05, DIMMER_06, DIMMER_07, DIMMER_08,
            DIMMER_09, DIMMER_10, DIMMER_11, DIMMER_12, DIMMER_13, DIMMER_14, DIMMER_15, DIMMER_16
        )
    }

    /**
     * Returns a [Light] instance controlling the specified [lightIds].
     */
    override fun lights(vararg lightIds: String): Light {
        return KinconyLights(lightIds.toSet())
    }

    override suspend fun lightOn() {
        KinconyLights(ShellyPro2PMDimmerHttp.ALL_CHANNELS).lightOn()
    }

    override suspend fun lightOff() {
        KinconyLights(ShellyPro2PMDimmerHttp.ALL_CHANNELS).lightOff()
    }

    override suspend fun setBrightness(value: Int) {
        KinconyLights(ShellyPro2PMDimmerHttp.ALL_CHANNELS).setBrightness(value)
    }

    /**
     * Sends the command to turn the light on or off.
     *
     * @param lightId The ID of the light.
     * @param on Whether to turn the light on (true) or off (false).
     * @throws RemoteOpException If the HTTP request fails or the response indicates an error.
     */
    private suspend fun sendSwitchCommand(lightId: String, on: Boolean) {
        setBrightnessForLight(lightId, if (on) 100 else 0)
    }

    /**
     * Sets the brightness for a specific light.
     *
     * @param lightId The ID of the light.
     * @param value The brightness value to set (0-100).
     * @throws RemoteOpException If the HTTP request fails or the response indicates an error.
     */
    private suspend fun setBrightnessForLight(lightId: String, value: Int) {
        val mappedValue = mapToRange(value, workingRanges[lightId] ?: DEFAULT_RANGE)
        val url = "http://$hostname/dimmer_ctl.cgi?Dimmer$lightId=$mappedValue&postpwd=$password"
        val response: HttpResponse = client.get(url)

        if (response.status.value !in 200..299) {
            throw RemoteOpException("Failed to set brightness for light $lightId: HTTP ${response.status.value}")
        }
    }

    private fun mapToRange(value: Int, range: IntRange): Int {
        if (value == 0) {
            return 0  // Use 0 for turning off
        }
        val rangeLength = range.last - range.first
        return ((rangeLength / 100.0) * value + range.first).toInt()
    }

    inner class KinconyLights(private val lightIds: Set<String>) : Light {

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
}
