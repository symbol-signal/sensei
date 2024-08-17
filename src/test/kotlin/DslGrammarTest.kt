package symsig.sensei

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import symsig.sensei.grammar.room
import symsig.sensei.grammar.rooms
import symsig.sensei.grammar.seconds

class DslGrammarTest : StringSpec({

    beforeTest {
        rooms.clear()
    }

    "seconds extension property should work correctly" {
        val duration = 5.seconds
        duration.inWholeSeconds shouldBe 5
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

    "should create a rule in a room with correct condition" {
        room("Kitchen") {
            rules {
                keep("Condition State Rule") {
                    whilst presenceIn "Kitchen" extends 5.seconds
                }
            }
        }

        val createdRule = rooms[0].rules.conditionStateRules[0]
        createdRule.description shouldBe "Condition State Rule"
//            createdRule.condition.area shouldBe "Kitchen"
//        createdRule.condition.extendedFor shouldBe 5.seconds
    }

    "should create multiple rooms" {
        room("Living Room") {}
        room("Bedroom") {}
        room("Kitchen") {}

        rooms.size shouldBe 3
        rooms.map { it.name } shouldContainExactly listOf("Living Room", "Bedroom", "Kitchen")
    }
})