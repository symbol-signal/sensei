package symsig.sensei.rules

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * Script definition for .rule.kts files.
 */
@KotlinScript(
    fileExtension = "rule.kts",
    compilationConfiguration = RuleScriptCompilationConfig::class,
    evaluationConfiguration = RuleScriptEvaluationConfig::class
)
abstract class RuleScript

object RuleScriptCompilationConfig : ScriptCompilationConfiguration({
    defaultImports(
        "symsig.sensei.devices.*",
        "symsig.sensei.devices.PresenceState.*",
        "symsig.sensei.devices.dimmer.*",
        "symsig.sensei.devices.relay.*",
        "symsig.sensei.util.time.*",
        "kotlinx.coroutines.*",
        "kotlinx.coroutines.flow.*",
        "kotlin.time.Duration.Companion.seconds",
        "kotlin.time.Duration.Companion.minutes",
        "kotlin.time.Duration.Companion.hours",
        "java.time.LocalDateTime"
    )

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    implicitReceivers(RuleContext::class)
})

object RuleScriptEvaluationConfig : ScriptEvaluationConfiguration({})
