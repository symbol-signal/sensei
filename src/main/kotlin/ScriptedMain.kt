package symsig.sensei

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import symsig.sensei.rules.RuleContext
import symsig.sensei.rules.RuleLoader
import symsig.sensei.services.SolarService
import symsig.sensei.util.mqtt.MqttClientAdapter
import java.io.File

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    val rulesDir = File(args.firstOrNull() ?: "rules")

    val job = coroutineContext.job
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "shutdown_sequence_executed" }
        runBlocking { job.cancelAndJoin() }
    })

    val solarService = SolarService(HttpClient(CIO))
    solarService.update()
    launch { solarService.runUpdate() }

    runMqttApplication("central.local", 1883) { client ->
        val context = RuleContext(MqttClientAdapter(client), solarService, this)
        val results = RuleLoader(context).loadAll(rulesDir)
        val failed = results.count { it.isFailure }
        if (failed > 0) {
            log.error { "rules_failed count=$failed" }
        }
    }

    log.info { "application_finished_gracefully" }
}
