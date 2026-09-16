package com.gc.waravi.models

import com.google.gson.annotations.SerializedName

data class RoomMetadata(
    @SerializedName("requestedEncoding")
    val requestedEncoding: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("mirror")
    val mirror: Boolean = false,
)