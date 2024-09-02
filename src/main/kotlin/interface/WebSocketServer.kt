package symsig.sensei.`interface`

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

interface PresenceSensorRemoteMessaging {
    fun sendMessageToPresenceSensors(message: JsonObject)
    fun addPresenceSensorMessageHandler(handler: WebSocketMessageHandler)
    fun removePresenceSensorMessageHandler(handler: WebSocketMessageHandler)
}

data class JsonMessage(
    val payload: JsonObject,
    val timestamp: Instant = Instant.now(),
)

typealias WebSocketMessageHandler = (JsonMessage) -> Unit

class WebSocketServer(private val port: Int) : PresenceSensorRemoteMessaging {
    private val presenceSensorHandlers: CopyOnWriteArrayList<WebSocketMessageHandler> = CopyOnWriteArrayList()
    private val presenceSensorClients: CopyOnWriteArrayList<DefaultWebSocketSession> = CopyOnWriteArrayList()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var server: NettyApplicationEngine? = null

    private fun Application.module() {
        install(WebSockets)
        presenceSensorModule()
    }

    internal fun Application.presenceSensorModule() {
        routing {
            webSocket("/sensor/presence") {
                presenceSensorClients += this
                try {
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val receivedText = frame.readText()
                        val jsonObject = Json.parseToJsonElement(receivedText).jsonObject
                        val wsMessage = JsonMessage(jsonObject)
                        presenceSensorHandlers.forEach { handler ->
                            try {
                                handler(wsMessage)
                            } catch (e: Exception) {
                                logger.error(e) { "[sensor_handler_error] handler=[$handler]" }
                            }
                        }
                    }
                } finally {
                    presenceSensorClients -= this
                }
            }
        }
    }

    fun start(wait: Boolean = true) {
        server = embeddedServer(Netty, port = port) {
            module()
        }.apply {
            start(wait)
        }
    }

    fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        server?.stop(gracePeriodMillis, timeoutMillis)
    }

    override fun addPresenceSensorMessageHandler(handler: WebSocketMessageHandler) {
        presenceSensorHandlers += handler
    }

    override fun removePresenceSensorMessageHandler(handler: WebSocketMessageHandler) {
        presenceSensorHandlers -= handler
    }

    override fun sendMessageToPresenceSensors(message: JsonObject) {
        presenceSensorClients.forEach { client ->
            launchMessageSendingCoroutine(client, message)
        }
    }

    private fun launchMessageSendingCoroutine(client: DefaultWebSocketSession, message: JsonObject) {
        coroutineScope.launch {
            try {
                client.send(Frame.Text(message.toString()))
            } catch (e: Exception) {
                logger.error(e) { "[send_sensor_message_error] sensor_client=[$client] message=[$message]" }
            }
        }
    }
}
