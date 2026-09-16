package com.gc.waravi.models

import com.google.gson.annotations.SerializedName

data class SocketData(
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("senderId")
    val senderId: String,
    @SerializedName("receiverId")
    val receiverId: String,
    @SerializedName("room")
    val room: String,
    @SerializedName("type")
    val type: String = "voip_push"
)
