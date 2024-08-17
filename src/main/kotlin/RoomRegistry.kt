package symsig.sensei

import symsig.sensei.grammar.Room

object RoomRegistry {

    private val _rooms: MutableMap<String, Room> = mutableMapOf()

    val rooms: Map<String, Room>
        get() = _rooms.toMap()

    fun addRoom(room: Room) {
        if (_rooms.containsKey(room.name)) {
            // TODO
            throw IllegalStateException()
        }
        _rooms[room.name] = room
    }

    fun getRoom(name: String): Room? = _rooms[name]

    fun getAllRooms(): List<Room> = _rooms.values.toList()
}
