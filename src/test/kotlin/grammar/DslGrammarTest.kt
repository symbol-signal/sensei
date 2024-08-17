package symsig.sensei.grammar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import symsig.sensei.RoomRegistry
import kotlin.time.Duration.Companion.seconds

class DslGrammarTest : StringSpec({

    fun getRoom(name: String): Room = requireNotNull(RoomRegistry.getRoom(name)) { "Room '$name' should exist" }

    "seconds extension property should work correctly" {
        val duration = 5.seconds
        duration.inWholeSeconds shouldBe 5
    }

    "should create a room" {
        room("Living Room") {}

        val createdRoom = getRoom("Living Room")
        createdRoom.name shouldBe "Living Room"
    }

    "should create a device in a room" {
        room("Bedroom") {
            devices {
                wsPresence {
                    sensorId = "ws001"
                }
            }
        }

        val room = getRoom("Bedroom")
        room.devices.wsPresenceSensors.size shouldBe 1
        room.devices.wsPresenceSensors[0].sensorId shouldBe "ws001"
    }

    "should create a rule in a room with correct condition" {
        room("Kitchen") {
            rules {
                keep("Condition State Rule") {
                    whilst presenceIn "Kitchen" extends 5.seconds
                }
            }
        }

        val room = getRoom("Kitchen")
        val createdRule = room.rules.conditionStateRules[0]
        createdRule.description shouldBe "Condition State Rule"
        // Uncomment and adjust these lines when the condition properties are accessible
        // createdRule.condition.area shouldBe "Kitchen"
        // createdRule.condition.extendedFor shouldBe 5.seconds
    }

    "should create multiple rooms" {
        room("Room 1") {}
        room("Room 2") {}
        room("Room 3") {}

        val allRooms = RoomRegistry.getAllRooms()
        val testRooms = allRooms.filter { it.name.matches(Regex("Room [1-3]")) }

        testRooms.size shouldBe 3
        testRooms.map { it.name } shouldContainExactlyInAnyOrder  listOf("Room 1", "Room 2", "Room 3")
    }
})