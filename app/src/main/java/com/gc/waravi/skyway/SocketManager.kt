package com.gc.waravi.skyway

import android.util.Log
import com.gc.waravi.models.SocketData
import com.gc.waravi.skyway.call.PushEvent
import com.gc.waravi.skyway.call.SocketEvent
import com.gc.waravi.utils.Constant
import com.google.gson.Gson
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object SocketManager {
    private val mSocket: Socket by lazy {
        IO.socket(Constant.NetWork.BASE_SERVER_URL, IO.Options().apply { path = "/${Constant.NetWork.AUTH_SERVER_URL_PATH}/socket.io" })
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutableEvents: MutableSharedFlow<SocketEvent> = MutableSharedFlow()
    val socketEvents: SharedFlow<SocketEvent> = mutableEvents.asSharedFlow()

    private fun emitEvent(event: SocketEvent){
        scope.launch {
            mutableEvents.emit(event)
        }
    }

    fun connectSocket(id: String? = null){
        mSocket.offAnyIncoming()
        mSocket.offAnyOutgoing()
        mSocket.off()
        mSocket.on("call") { args ->
            if(args.isNotEmpty()){
                val data = Gson().fromJson(args[0].toString(), SocketData::class.java)
                Log.d(this::class.simpleName, "on socket data received: $data")
                when(data.type){
                    PushEvent.PushNotification.TYPE_VOIP -> emitEvent(SocketEvent.IncomingCall(data))
                    PushEvent.PushNotification.TYPE_END,
                    PushEvent.PushNotification.TYPE_CANCEL,
                    PushEvent.PushNotification.TYPE_BUSY,
                    PushEvent.PushNotification.TYPE_DECLINE -> emitEvent(SocketEvent.EndCall(data))
                }
            }
        }
        mSocket.on("connect"){ args ->
            Log.d(this::class.simpleName, "socket connected...${SkywayManager.selfId}")
            if(SkywayManager.selfId.isNotEmpty()){
                mSocket.emit("register", id ?: SkywayManager.selfId)
            }
        }
        mSocket.on("reconnect"){ args ->
            Log.d(this::class.simpleName, "socket reconnect...${args[0]}")
        }
        mSocket.on("reconnect_failed"){ args ->
            Log.d(this::class.simpleName, "socket reconnect_failed...${args[0]}")
        }
        mSocket.on("disconnect"){ args ->
            Log.d(this::class.simpleName, "socket disconnected...")
            args.forEach { arg ->
                Log.d(this::class.simpleName, "disconnect details...${arg}")
            }
        }
        mSocket.connect()
    }

    fun checkSocketConnection(){
        if(!mSocket.connected()){
            this.connectSocket()
        }
    }

    fun isConnected(): Boolean{
        return mSocket.connected()
    }

    fun reconnectSocketIfNeeded(){
        if(mSocket.connected().not()){
            reconnectSocket()
        }
    }

    fun reconnectSocket(){
        this.disconnectSocket()
        this.connectSocket()
    }

    fun disconnectSocket(){
        mSocket.off()
        mSocket.disconnect()
    }

    fun emitSocketEvent(event: String, data: String){
        if(mSocket.connected()){
            mSocket.emit(event, data)
        }
    }

    fun emitSocketEvent(event: String, data: String, callback: Ack){
        if(!mSocket.connected()){
            mSocket.connect()
        }
        mSocket.emit(event, data, callback)
    }

    fun emitCallEvent(calleeId: String, data: String, callback: Ack){
        if(!mSocket.connected()){
            mSocket.connect()
        }
        mSocket.emit("call", calleeId, data, callback)
    }
}