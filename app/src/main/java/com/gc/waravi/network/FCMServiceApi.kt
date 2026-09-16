package com.gc.waravi.network

import com.gc.waravi.models.FCMAndroidMessage
import com.gc.waravi.utils.Constant
import retrofit2.http.Body
import retrofit2.http.POST

interface FCMServiceApi {
    @POST("/v1/projects/${Constant.NetWork.FCM_APP_ID}/messages:send")
    suspend fun sendVoIPNotification(
        @Body data: FCMAndroidMessage
    ): Any

}
