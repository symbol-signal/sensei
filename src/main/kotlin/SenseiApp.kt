package symsig.sensei

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun main() {

    embeddedServer(Netty, port = 8080) {
        install(WebSockets)
        routing {
            webSocket("/") {
                send("You are connected!")
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    send("You said: $receivedText")
                }
            }
        }
    }.start(wait = true)
}
