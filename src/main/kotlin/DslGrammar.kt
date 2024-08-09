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
    room.init()
    rooms.add(room)
}

class Room(val name: String) {

    internal val devices = Devices()

    internal val rules: MutableList<Rule> = mutableListOf()

    fun devices(init: Devices.() -> Unit) {
        log.debug("Defining devices")
        devices.init()
    }

    fun rules(init: RuleBuilder.() -> Unit) {
        log.debug("Defining rules")
        RuleBuilder(this).init()
    }

    internal fun addRule(rule: Rule) {
        rules.add(rule)
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

class RuleBuilder(private val room: Room) {

    fun rule(description: String, init: Rule.() -> Unit) {
        val rule = Rule(description)
        rule.init()
        room.addRule(rule)
    }
}

class Rule(val description: String) {

    lateinit var condition: PresenceCondition
        internal set

    val whenever = ConditionFactory(this)

//    lateinit var action: Action

//    fun perform(init: Action.() -> Unit) {
//        action = Action()
//        action.init()
//    }
}

class ConditionFactory(private val rule: Rule) {

    infix fun presenceIn(area: String): PresenceCondition {
        return PresenceCondition(area).also { rule.condition = it }
    }
}

class PresenceCondition(area: String) {

    internal var extendedFor: Duration? = null

    infix fun extends(duration: Duration): PresenceCondition {
        extendedFor = duration
        print(duration)
        return this
    }
}

class Action()
