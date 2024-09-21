package symsig.sensei.device.dimmer.shelly

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import symsig.sensei.device.RemoteOpException
import symsig.sensei.device.dimmer.Dimmer
import symsig.sensei.device.dimmer.Light

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