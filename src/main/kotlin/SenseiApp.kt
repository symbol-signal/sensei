package symsig.sensei

import symsig.sensei.device.PresenceSensors
import symsig.sensei.`interface`.WebSocketServer

fun main() {
    val wsServer = WebSocketServer(8080)
    val presenceSensor = PresenceSensors.sensord("id", wsServer::sendMessageToPresenceSensors)
    wsServer.addSensorMessageHandler(presenceSensor::handleSensorJsonMessage)
}