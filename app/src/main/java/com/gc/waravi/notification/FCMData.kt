package com.gc.waravi.notification

import com.google.gson.annotations.SerializedName

data class FCMData(
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("callerId")
    val callerId: String,
    @SerializedName("calleeId")
    val calleeId: String,
    @SerializedName("room")
    val room: String,
    @SerializedName("type")
    val type: String = "voip_push",
)
