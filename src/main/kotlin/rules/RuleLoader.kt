package symsig.sensei.rules

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * Loads and executes .rule.kts scripts with the provided context.
 */
class RuleLoader(private val context: RuleContext) {

    private val scriptingHost = BasicJvmScriptingHost()

    /**
     * Load and execute a single rule script.
     */
    fun load(file: File): Result<Unit> {
        log.info { "rule_loading file=${file.name}" }

        val result = scriptingHost.eval(
            file.toScriptSource(),
            RuleScriptCompilationConfig,
            ScriptEvaluationConfiguration {
                implicitReceivers(context)
            }
        )

        return when (val evalResult = result.valueOrNull()?.returnValue) {
            is ResultValue.Value, is ResultValue.Unit, null -> {
                if (result.reports.any { it.severity == ScriptDiagnostic.Severity.ERROR }) {
                    val errors = result.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }
                    log.error { "rule_load_failed file=${file.name} errors=${errors.map { it.message }}" }
                    Result.failure(Exception(errors.joinToString("\n") { it.message }))
                } else {
                    log.info { "rule_loaded file=${file.name}" }
                    Result.success(Unit)
                }
            }
            is ResultValue.Error -> {
                log.error(evalResult.error) { "rule_execution_failed file=${file.name}" }
                Result.failure(evalResult.error)
            }
            is ResultValue.NotEvaluated -> {
                log.error { "rule_not_evaluated file=${file.name}" }
                Result.failure(Exception("Script was not evaluated"))
            }
        }
    }

    /**
     * Load all .rule.kts files from a directory.
     */
    fun loadAll(directory: File): List<Result<Unit>> {
        if (!directory.isDirectory) {
            log.warn { "rules_directory_not_found path=${directory.absolutePath}" }
            return emptyList()
        }

        val ruleFiles = directory.listFiles { f -> f.extension == "kts" && f.name.endsWith(".rule.kts") }
            ?: return emptyList()

        log.info { "rules_found count=${ruleFiles.size} directory=${directory.absolutePath}" }

        return ruleFiles.map { load(it) }
    }
}
