package com.gc.waravi.network

import android.util.Log
import com.gc.waravi.utils.Constant
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

class AuthRepository {

    suspend fun checkVerifyCode(code: String, peerId: String?, onSuccess: (AuthResponse?) -> Unit, onFailure: (BaseResponse?) -> Unit) {
        try {
            val response = authService.check(code, identity = peerId, appId = Constant.NetWork.AUTH_APP_ID, domain = Constant.NetWork.AUTH_APP_DOMAIN)
            if (response.isSuccessful){
                onSuccess(response.body())
            } else{
                onFailure(Gson().fromJson(response.errorBody()?.string(), BaseResponse::class.java))
            }
        } catch (ex: Exception) {
            Log.e(this.javaClass.simpleName, ex.message ?: "Unknown")
            onFailure(null)
        }
    }

    suspend fun updatePeer(code: String, peerId: String): BaseResponse? {
        return try {
            val response = authService.update(
                code,
                peerId,
                appId = Constant.NetWork.AUTH_APP_ID,
                domain = Constant.NetWork.AUTH_APP_DOMAIN
            )
            response.body()
        } catch (ex: Exception) {
            null
        }
    }

    suspend fun getRoomToken(identify: String, roomName: String, type: String? = null) : SkywayAuthResult{
        return try {
            val response = authService.getRoomToken(identify, roomName, type)
            val token =  response.body()?.accessToken
            if (token.isNullOrEmpty().not()) SkywayAuthResult.SkywayAuthSuccessResult(token!!)
            else SkywayAuthResult.SkywayAuthFailureResult("Token is empty.")
        } catch (ex: Exception) {
            SkywayAuthResult.SkywayAuthFailureResult(ex.message.toString())
        }
    }

    suspend fun getToken() : SkywayAuthResult{
        return try {
            val response = authService.getToken(Constant.NetWork.AUTH_APP_ID)
            val token =  response.body()?.accessToken
            if (token.isNullOrEmpty().not()) SkywayAuthResult.SkywayAuthSuccessResult(token!!)
            else SkywayAuthResult.SkywayAuthFailureResult("Token is empty.")
        } catch (ex: Exception) {
            SkywayAuthResult.SkywayAuthFailureResult(ex.message.toString())
        }
    }

    suspend fun register(identify: String, code: String? = null) : RegisterResponse?{
        return try {
            val response = authService.register(identify, code, Constant.NetWork.AUTH_APP_ID, Constant.NetWork.AUTH_APP_DOMAIN)
            response.body()
        } catch (ex: Exception) {
            null
        }
    }

}

data class AuthResponse(
    @SerializedName("key") val apiKey: String,
    @SerializedName("credential") val credential: Credential,
)

data class Credential(
    @SerializedName("peerId") val peerId: String?,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("ttl") val ttl: Long,
    @SerializedName("authToken") val authToken: String
)

data class SkywayAuthResponse(
    @SerializedName("access_token") val accessToken: String,
)

data class RegisterResponse(
    @SerializedName("authKey") val authKey: String,
)

data class BaseResponse(val statusCode: Int, val message: String)
