package symsig.sensei.grammar

import kotlin.time.Duration

interface Condition

interface State

class ConditionStateRule(val description: String) {

    lateinit var condition: Condition
        internal set

    lateinit var state: State
        internal set
}

class ConditionStateRuleBuilder(val rule: ConditionStateRule) {

    val whilst = ConditionFactory(rule)

    val ensure = StateFactory(rule)
}

class ConditionFactory(private val rule: ConditionStateRule) {

    infix fun presenceIn(area: String): PresenceCondition {
        return PresenceCondition(area).also { rule.condition = it }
    }
}

class PresenceCondition(area: String) : Condition {

    internal var extendedFor: Duration? = null

    infix fun extends(duration: Duration): PresenceCondition {
        extendedFor = duration
        print(duration)
        return this
    }
}


class StateFactory(private val rule: ConditionStateRule) {

    infix fun lights(lightsId: String): LightsState {
        return LightsState(lightsId).also { rule.state = it }
    }
}

class LightsState(lightsId: String): State {

    infix fun kept(s: String) {

    }
}
