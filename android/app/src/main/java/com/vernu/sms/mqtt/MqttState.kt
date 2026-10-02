package com.vernu.sms.mqtt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MqttConnectionState { OFF, CONNECTING, CONNECTED }

// The live connection's state, for the Device health screen and the workers
object MqttState {
    private val _state = MutableStateFlow(MqttConnectionState.OFF)
    val state: StateFlow<MqttConnectionState> = _state.asStateFlow()

    val value: MqttConnectionState get() = _state.value

    fun set(state: MqttConnectionState) {
        _state.value = state
    }
}
