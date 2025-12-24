package symsig.sensei.devices.dimmer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import symsig.sensei.devices.dimmer.KinconyD16Dimmer.Channel
import symsig.sensei.util.mqtt.FakeMqtt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KinconyD16DimmerTest {

    @Test
    fun `mapFromRange with effective range 20 to 40`() = runTest(UnconfinedTestDispatcher()) {
        val mqtt = FakeMqtt()
        val dimmer = KinconyD16Dimmer(
            mqtt, "dimmer/state", "dimmer/set", backgroundScope,
            effectiveRanges = mapOf(Channel.Ch1 to 20..40)
        )

        fun assertMaps(hardwareValue: Int, expectedLogical: Int, description: String) {
            mqtt.emitState("dimmer/state", """{"dimmer1":{"value":$hardwareValue}}""")
            assertEquals(expectedLogical, dimmer.state.value[Channel.Ch1], description)
        }

        // Zero is always off
        assertMaps(0, 0, "hw=0 → off")

        // Below range minimum - light is on, should map to minimum "on" value
        assertMaps(1, 1, "hw=1 → on (below range)")
        assertMaps(19, 1, "hw=19 → on (just below range)")

        assertMaps(20, 1, "hw=20 → on (range start)")  // At range minimum - light is on, maps to 1
        assertMaps(30, 50, "hw=30 → 50 (range middle)")
        assertMaps(40, 99, "hw=40 → 99 (range end)")
        assertMaps(50, 99, "hw=50 → 99 (above range)")
    }
}
