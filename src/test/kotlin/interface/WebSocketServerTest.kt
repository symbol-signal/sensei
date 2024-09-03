package symsig.sensei.`interface`

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

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
                MessageHandlerResult.Accepted
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

    "test sending message to connected clients" {
        testApplication {
            install(WebSockets)

            val server = WebSocketServer(8080)

            application {
                server.apply { presenceSensorModule() }
            }

            val client = createClient {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }

            client.webSocket("/sensor/presence") {
                val receivedMessage = async {
                    withTimeout(1000) {
                        (incoming.receive() as? Frame.Text)?.readText()
                    }
                }

                server.sendMessageToPresenceSensors(buildJsonObject {
                    put("sensorId", "42")
                })

                val receivedText = receivedMessage.await() ?: ""
                val receivedJson = Json.parseToJsonElement(receivedText).jsonObject

                receivedJson["sensorId"]?.jsonPrimitive?.content shouldBe "42"
            }
        }
    }
})
