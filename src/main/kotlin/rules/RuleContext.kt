package symsig.sensei.rules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import symsig.sensei.services.SolarService
import symsig.sensei.util.mqtt.Mqtt
import symsig.sensei.util.time.*
import java.time.LocalDateTime

/**
 * Context provided to rule scripts as implicit receiver.
 * All properties and functions are available as top-level in scripts.
 */
class RuleContext(
    val mqtt: Mqtt,
    val solar: SolarService,
    val scope: CoroutineScope
) {
    // Time windows - scripts can use: `in daytime`, `in evening`, etc.
    val dayStart get() = solar.sunrise laterOf time("07:00")
    val dayEnd get() = solar.sunset laterOf time("18:00")
    val daytime get() = window(dayStart, dayEnd)
    val evening get() = window(dayEnd, "22:00")
    val windingDown get() = window("22:00", "23:00")
    val night get() = window("23:00", dayStart)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)  // Coroutine helper
    fun now(): LocalDateTime = LocalDateTime.now()
}
