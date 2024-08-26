package symsig.sensei.`interface`

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

typealias WebSocketMessageHandler = suspend (JsonObject) -> Unit

class WebSocketServer(port: Int) {

    private val sensorHandlers: CopyOnWriteArrayList<WebSocketMessageHandler> = CopyOnWriteArrayList()

    private val server: NettyApplicationEngine = embeddedServer(Netty, port = port) {
        install(WebSockets)
        routing {
            webSocket("/sensor") {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    val jsonObject = Json.parseToJsonElement(receivedText).jsonObject
                    sensorHandlers.forEach { handler ->
                        try {
                            handler(jsonObject)
                        } catch (e: Exception) {
                            logger.error(e) {"[sensor_handler_error] handler=[$handler]"}
                        }
                    }
                }
            }
        }
    }

    fun start(wait: Boolean) {
        server.start(wait)
    }

    fun addSensorMessageHandler(handler: WebSocketMessageHandler) {
        sensorHandlers.add(handler)
    }

    fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        server.stop(gracePeriodMillis, timeoutMillis)
    }
}
