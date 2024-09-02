package symsig.sensei.`interface`

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

class WebSocketModuleTest : StringSpec({

    "test WebSocket module" {
        testApplication {
            val messageChannel = Channel<String>()
            val server = WebSocketServer(8080)

            application {
//                install(WebSockets) // This is the server-side install
                server.apply { module() }
            }

            server.addPresenceSensorMessageHandler { message ->
                messageChannel.trySend(message.payload.toString())
            }

            val client = createClient {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }

            client.webSocket("/sensor/presence") {
                // Send a message to the server
                send(Frame.Text("Hello, Server!"))

                // Wait for the response
                withTimeout(5000) {
                    val response = messageChannel.receive()
                    response shouldBe "Hello, Server!"
                }
            }
        }
    }
})
