package com.gc.waravi.models

import com.google.gson.annotations.SerializedName

data class AtpopPushData(
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("callerId")
    val callerId: String,
    @SerializedName("calleeId")
    val calleeId: String,
    @SerializedName("room")
    val room: String,
    @SerializedName("type")
    val type: String = "call"
)
