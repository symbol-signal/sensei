package symsig.sensei

import io.github.nomisrev.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*

fun main() {
    playJson()
//    runWsServer()
}

private fun runWsServer() {
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

private fun playJson() {
    val jsonString = """{"name": "sensor1", "value": 42, "ojb":  {"number":  "3"}}"""
    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

// Query and get the JsonPrimitive
    val result: JsonPrimitive? = JsonPath.path("ojb.number").getOrNull(jsonObject) as? JsonPrimitive

// Extract the value as Any
    val value: Any? = when {
        result?.isString == true -> result.content
        result?.booleanOrNull != null -> result.boolean
        result?.intOrNull != null -> result.int
        result?.longOrNull != null -> result.long
        result?.floatOrNull != null -> result.float
        result?.doubleOrNull != null -> result.double
        else -> null
    }

    println(value) // Prints the value as an object

}