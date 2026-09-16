package com.gc.waravi.network

import com.gc.waravi.models.AtpopPushData
import com.gc.waravi.utils.Constant.NetWork.AUTH_SERVER_URL_PATH
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthServiceApi {
    @FormUrlEncoded
    @POST("$AUTH_SERVER_URL_PATH/update-auth")
    suspend fun update(
        @Field("code") code: String, @Field("identity") identity: String, @Field("appId") appId: String,
        @Field("domain") domain: String
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("$AUTH_SERVER_URL_PATH/verification")
    suspend fun check(
        @Field("code") code: String,
        @Field("identity") identity: String?,
        @Field("appId") appId: String,
        @Field("domain") domain: String
    ): Response<AuthResponse>

    @GET("$AUTH_SERVER_URL_PATH/roomToken")
    suspend fun getRoomToken(
        @Field("identity") identity: String, @Field("room") roomName: String, @Field("type") roomType: String?
    ): Response<SkywayAuthResponse>

    @GET("$AUTH_SERVER_URL_PATH/token")
    suspend fun getToken(@Query("appId") appId: String): Response<SkywayAuthResponse>

    @FormUrlEncoded
    @POST("$AUTH_SERVER_URL_PATH/register")
    suspend fun register(
        @Field("identity") identity: String,
        @Field("code") code: String?,
        @Field("appId") appId: String?,
        @Field("domain") domain: String?
    ): Response<RegisterResponse>

    @POST("$AUTH_SERVER_URL_PATH/send-atpop-push")
    suspend fun sendAtPopPush(
        @Body data: AtpopPushData
    ): Response<Any>

}
