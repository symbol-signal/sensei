package symsig.sensei

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf

class DslGrammarTest : StringSpec({

    beforeTest {
        rooms.clear()
    }

    "should create a room" {
        room("Living Room") {}

        rooms.size shouldBe 1
        rooms[0].name shouldBe "Living Room"
    }

    "should create a device in a room" {
        room("Bedroom") {
            devices {
                wsPresence {
                    sensorId = "ws001"
                }
            }
        }

        rooms[0].devices.wsPresenceSensors.size shouldBe 1
        rooms[0].devices.wsPresenceSensors[0].sensorId shouldBe "ws001"
    }

    "should create a rule in a room" {
        room("Kitchen") {
            rules {
                rule("Test Rule") {
                    whenever presenceIn "Kitchen" extends 5.seconds
                }
            }
        }

        rooms[0].rules.rules.size shouldBe 1
        rooms[0].rules.rules[0].shouldBeInstanceOf<Rule>()
        rooms[0].rules.rules[0].whenever.shouldBeInstanceOf<Condition>()
    }

    "should create multiple rooms" {
        room("Living Room") {}
        room("Bedroom") {}
        room("Kitchen") {}

        rooms.size shouldBe 3
        rooms.map { it.name } shouldContainExactly listOf("Living Room", "Bedroom", "Kitchen")
    }

    "seconds extension property should work correctly" {
        val duration = 5.seconds
        duration.inWholeSeconds shouldBe 5
    }
})