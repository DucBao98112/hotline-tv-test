package com.gc.waravi.models

import com.gc.waravi.notification.FCMData
import com.google.gson.annotations.SerializedName

data class FCMPush(
    @SerializedName("data")
    val data: FCMData,
    @SerializedName("token")
    val token: String,
    @SerializedName("android")
    val payload: AndroidPayload = AndroidPayload(),
    @SerializedName("notification")
    val notification: NotificationBody? = null
)

data class AndroidPayload(
    @SerializedName("priority")
    val priority: String = "high",
)

data class FCMAndroidMessage(
    @SerializedName("message")
    val message: FCMPush
)

data class NotificationBody(
    @SerializedName("title")
    val title: String,
    @SerializedName("body")
    val body: String,
)