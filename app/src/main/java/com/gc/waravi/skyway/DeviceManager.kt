package com.gc.waravi.skyway

import android.content.Context
import com.gc.waravi.BuildConfig
import com.gc.waravi.models.DeviceInfoEntity
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.Utils
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object DeviceManager {
    suspend fun updateDeviceInfo(context: Context, peerId: String, token: String, deviceId: String, authKey: String? = null, reductionEnable: Boolean = false){
        if (peerId.isBlank()) return
        val deviceType = if (Utils.isRunningOnTV(context)) Constant.FireBaseFireStore.DEVICE_TYPE_ANDROID_TV
        else Constant.FireBaseFireStore.DEVICE_TYPE_ANDROID

        val deviceInfo = hashMapOf(
            Constant.FireBaseFireStore.KEY_FCM_TOKEN to token,
            Constant.FireBaseFireStore.KEY_DEVICE_ID to deviceId,
            Constant.FireBaseFireStore.KEY_DEVICE_TYPE to deviceType,
            Constant.FireBaseFireStore.KEY_AUTH_KEY to authKey,
            Constant.FireBaseFireStore.KEY_REDUCTION_KEY to reductionEnable.toString()
        )

        Firebase.firestore.collection(BuildConfig.FirestoreCollection)
            .document(peerId).set(deviceInfo)
            .await()
    }

    suspend fun getDeviceInfo(peerId: String) : DeviceInfoEntity?{
        if (peerId.isBlank()) return null
        val document = Firebase.firestore.collection(BuildConfig.FirestoreCollection)
            .document(peerId).get().await()
        val fcmToken = document.getString(Constant.FireBaseFireStore.KEY_FCM_TOKEN)
        val deviceId = document.getString(Constant.FireBaseFireStore.KEY_DEVICE_ID)
        val deviceType = document.getString(Constant.FireBaseFireStore.KEY_DEVICE_TYPE)
        val authKey = document.getString(Constant.FireBaseFireStore.KEY_AUTH_KEY)
        val reduction = document.getString(Constant.FireBaseFireStore.KEY_REDUCTION_KEY) == "true"
        return if (fcmToken.isNullOrEmpty()) null else DeviceInfoEntity(uniqueNumber = peerId,
            fcmToken = fcmToken, deviceId = deviceId ?: "", deviceType = deviceType, authKey = authKey, loadReduction = reduction)
    }

    suspend fun deleteDevice(peerId: String){
        Firebase.firestore.collection(BuildConfig.FirestoreCollection)
            .document(peerId).delete().await()
    }
}