package com.gc.waravi.skyway.room

import android.content.Context
import com.gc.waravi.models.RoomMetadata
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.google.gson.Gson
import com.ntt.skyway.room.Room
import com.ntt.skyway.room.member.RoomMember
import java.util.Calendar

object RoomManager {

    suspend fun createP2pRoomSession(context: Context, roomName: String, onSuccess: (RoomSession?) -> Unit){
        val isMirrorEnable = PrefUtils.getVideoMirror(context)
        SkywayManager.ensureSkywayInit(context) {
            val metadata = Gson().toJson(RoomMetadata(mirror = isMirrorEnable))
            val memberInit = RoomMember.Init(SkywayManager.selfId, metadata)
            val isSfuRoom = isSfuRoom(roomName)
            val roomSession = RoomSession(context, roomName, memberInit, if (isSfuRoom) Room.Type.SFU else Room.Type.P2P)
            val isSuccess = roomSession.createRoom()
            onSuccess.invoke(if (isSuccess) roomSession else null)
        }
    }

    private fun isSfuRoom(roomName: String) : Boolean{
        if (roomName.length != 11) return false
        val currentDateStr = Utils.getFormattedTime(Calendar.getInstance().timeInMillis, "MMdd")
        val sfuRoomPrefix = Constant.SFU_ROOM_PREFIX.toString() + currentDateStr
        return roomName.startsWith(sfuRoomPrefix, false)
    }

}