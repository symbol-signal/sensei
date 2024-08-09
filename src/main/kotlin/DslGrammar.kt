package symsig.sensei

import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val log = LoggerFactory.getLogger("symsig.sensei")

val Int.seconds
    get() = this.toDuration(DurationUnit.SECONDS)

val rooms: MutableList<Room> = mutableListOf()

fun room(name: String, init: Room.() -> Unit) {
    val room = Room(name)
    log.debug("Creating room {}", name)
    println("hello")
    room.init()
    rooms.add(room)
}

class Room(val name: String) {

    internal val devices = Devices()

    internal val rules = Rules()

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

    internal val wsPresenceSensors: MutableList<WebSocketPresenceSensor> = mutableListOf()

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

    internal val rules: MutableList<Rule> = mutableListOf()

    fun rule(description: String, init: Rule.() -> Unit) {
        val rule = Rule(description)
        rule.init()
        rules.add(rule)
    }
}

class Rule(description: String) {

    var whenever = Condition()

//    lateinit var action: Action

//    fun perform(init: Action.() -> Unit) {
//        action = Action()
//        action.init()
//    }
}

class Condition() {

    infix fun presenceIn(area: String): PresenceCondition {
        return PresenceCondition(area)
    }
}

class PresenceCondition(area: String) {

    private var extendedFor: Duration? = null

    infix fun extends(duration: Duration): PresenceCondition {
        extendedFor = duration
        print(duration)
        return this
    }
}

class Action()
