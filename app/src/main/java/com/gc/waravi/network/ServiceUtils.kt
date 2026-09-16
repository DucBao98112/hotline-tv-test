package com.gc.waravi.network

import com.gc.waravi.notification.FirebaseUtils
import com.gc.waravi.utils.Constant
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val fcmService: FCMServiceApi = retrofitBase(Constant.NetWork.FCM_BASE_URL, AuthFcmInterceptor()).create(
    FCMServiceApi::class.java)
val authService: AuthServiceApi = retrofitBase(
    Constant.NetWork.BASE_SERVER_URL,
    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
).create(
    AuthServiceApi::class.java
)

fun retrofitBase(baseUrl: String, interceptor: Interceptor) : Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(createHttpClient(interceptor))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

fun createHttpClient(interceptor: Interceptor): okhttp3.OkHttpClient {
    val builder = okhttp3.OkHttpClient.Builder()
        .protocols(arrayListOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .readTimeout(Constant.NetWork.READ_TIMEOUT, TimeUnit.SECONDS)
        .connectTimeout(Constant.NetWork.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constant.NetWork.WRITE_TIMEOUT, TimeUnit.SECONDS)
    builder.addInterceptor(interceptor)
    builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
    return builder.build()
}

class AuthFcmInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${FirebaseUtils.getFcmOAuth2Token()}")
            .addHeader("Content-Type", "application/json")
        return chain.proceed(requestBuilder.build())
    }
}