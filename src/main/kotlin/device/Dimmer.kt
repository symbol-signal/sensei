package symsig.sensei.device

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

interface Dimmer {

    fun lightOn(lightId: String)

    fun lightOff(lightId: String)
}

class ShellyPro2PMDimmerHttp(private val hostname: String) : Dimmer {
    private val client = HttpClient(CIO)

    override fun lightOn(lightId: String) {
        sendCommand(lightId, true)
    }

    override fun lightOff(lightId: String) {
        sendCommand(lightId, false)
    }

    private fun sendCommand(lightId: String, on: Boolean) {
        runBlocking {
            try {
                val url = "http://$hostname/rpc/Light.Set?id=$lightId&on=$on"
                val response: HttpResponse = client.get(url)

                if (response.status.value in 200..299) {
                    println("Command sent successfully")
                } else {
                    println("Error: ${response.status.value}")
                }
            } catch (e: Exception) {
                println("Error sending command: ${e.message}")
            }
        }
    }
}