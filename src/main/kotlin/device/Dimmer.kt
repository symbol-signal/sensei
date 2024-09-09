package symsig.sensei.device

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

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
