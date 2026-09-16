package com.gc.waravi.models

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

data class DeviceInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "unique_number") val uniqueNumber: String,
    @ColumnInfo(name = "fcm_token") val fcmToken: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_type") val deviceType: String? = null,
    @ColumnInfo(name = "authKey") val authKey: String? = null,
    @ColumnInfo(name = "reduction") val loadReduction: Boolean = false
)
