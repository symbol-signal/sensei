package symsig.sensei.grammar

interface EventTrigger

interface Action

class TriggerActionRule(val description: String) { // TriggerActionRule

    lateinit var trigger: EventTrigger
        internal set

    lateinit var action: Action
        internal set
}

class TriggerActionRuleBuilder(val rule: TriggerActionRule) {

    val on = TriggerConditionFactory(rule)

    val perform = ActionFactory(rule)
}

class TriggerConditionFactory(private val triggerActionRule: TriggerActionRule) {

    infix fun entranceTo(area: String): EntranceCondition {
        return EntranceCondition(area).also { triggerActionRule.trigger = it }
    }
}

class EntranceCondition(area: String): EventTrigger

class ActionFactory(private val triggerActionRule: TriggerActionRule) {

    infix fun lights(lightsId: String): LightsAction {
        return LightsAction(lightsId).also { triggerActionRule.action = it }
    }
}

class LightsAction(lightsId: String): Action {

    infix fun turn(s: String) {

    }
}
