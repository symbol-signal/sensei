package symsig.sensei.grammar

import org.slf4j.LoggerFactory
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

    internal val triggerActionRules = mutableListOf<TriggerActionRule>()

    internal val conditionStateRules = mutableListOf<ConditionStateRule>()

    fun trigger(description: String, init: TriggerActionRule.() -> Unit) {
        val triggerActionRule = TriggerActionRule(description)
        triggerActionRule.init()
        triggerActionRules.add(triggerActionRule)
    }

    fun keep(description: String, init: ConditionStateRuleBuilder.() -> Unit) {
        val conditionStateRule = ConditionStateRule(description)
        ConditionStateRuleBuilder(conditionStateRule).init()
        conditionStateRules.add(conditionStateRule)
    }
}
