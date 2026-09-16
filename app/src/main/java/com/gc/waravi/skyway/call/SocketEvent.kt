package com.gc.waravi.skyway.call

import com.gc.waravi.models.SocketData

sealed class SocketEvent {
    data class IncomingCall(val data: SocketData) : SocketEvent()
    data class EndCall(val data: SocketData) : SocketEvent()
}
