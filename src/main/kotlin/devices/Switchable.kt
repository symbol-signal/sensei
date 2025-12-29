package symsig.sensei.devices

import kotlinx.coroutines.flow.StateFlow

interface Switchable {
    val isOn: StateFlow<Boolean>
    suspend fun turnOn()
    suspend fun turnOff()
}
