package symsig.sensei.`interface`

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class WebSocketModuleTest : StringSpec({

    "test JSON deserialized and handler notified" {
        testApplication {
            install(WebSockets)

            val messageChannel = Channel<JsonMessage>()
            val server = WebSocketServer(8080)

            application {
                server.apply { presenceSensorModule() }
            }

            server.addPresenceSensorMessageHandler { message: JsonMessage ->
                messageChannel.trySend(message)
            }

            val client = createClient {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }

            client.webSocket("/sensor/presence") {
                send(Frame.Text("""{"sensorId": "sen1"}"""))

                withTimeout(100) {
                    val payload: JsonObject = messageChannel.receive().payload
                    payload["sensorId"]?.jsonPrimitive?.content shouldBe "sen1"
                }
            }
        }
    }
})
