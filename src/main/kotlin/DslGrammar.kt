package symsig.sensei

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("symsig.sensei")

val rooms: MutableList<Room> = mutableListOf()

fun room(name: String, init: Room.() -> Unit) {
    val room = Room(name)
    log.debug("Creating room {}", name)
    room.init()
    rooms.add(room)
}

class Room(val name: String) {

    private val devices = Devices()

    private val rules = Rules()

    fun devices(init: Devices.() -> Unit) {
        log.debug("Defining devices")
        devices.init()
    }

    fun rules(init: Rules.() -> Unit) {
        log.debug("Defining rules")
        rules.init()
    }
}

class Devices() {

    private val wsPresenceSensors: MutableList<WebSocketPresenceSensor> = mutableListOf()

    fun wsPresence(init: WebSocketPresenceSensor.() -> Unit) {
        val sensor = WebSocketPresenceSensor()
        sensor.init()
        log.debug("Created {} sensor", sensor.sensorId)
        wsPresenceSensors.add(sensor)
    }
}

class WebSocketPresenceSensor() {
    var sensorId: String = ""
}

class Rules() {

    private val rules: MutableList<Rule> = mutableListOf()

    fun rule(description: String, init: Rule.() -> Unit) {
        val rule = Rule(description)
        rule.init()
        rules.add(rule)
    }
}

class Rule(description: String)