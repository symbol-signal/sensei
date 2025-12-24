package symsig.sensei.rules

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import symsig.sensei.services.SolarService
import symsig.sensei.util.mqtt.FakeMqtt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RuleScriptTest {

    @Test
    fun `simple rule script executes`() {
        val scope = CoroutineScope(Dispatchers.Default)

        try {
            val mqtt = FakeMqtt()
            val solar = SolarService(HttpClient(CIO))
            val context = RuleContext(mqtt, solar, scope)
            val loader = RuleLoader(context)

            // Create a temporary script file
            val scriptContent = """
                // Test script - just verify context is available
                val currentTime = now()
                val isDay = currentTime in daytime

                launch {
                    // Script executed successfully
                }
            """.trimIndent()

            val tempFile = File.createTempFile("test", ".rule.kts")
            tempFile.deleteOnExit()
            tempFile.writeText(scriptContent)

            val result = loader.load(tempFile)

            assertTrue(result.isSuccess, "Script should execute successfully: ${result.exceptionOrNull()?.message}")
        } finally {
            scope.cancel()
        }
    }
}
