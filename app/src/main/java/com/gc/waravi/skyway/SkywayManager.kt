package com.gc.waravi.skyway

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.network.AuthRepository
import com.gc.waravi.network.RegisterResponse
import com.gc.waravi.network.SkywayAuthResult
import com.gc.waravi.utils.AppPrefs
import com.ntt.skyway.core.SkyWayContext
import com.ntt.skyway.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

object SkywayManager {
    private lateinit var appPrefs: AppPrefs
    private val authRepository by lazy { AuthRepository() }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val TAG = this.javaClass.simpleName
    var selfId : String = ""

    fun initialize(application: BaseApplication){
        appPrefs = AppPrefs(application)
        selfId = appPrefs.selfId

        SkyWayContext.onErrorHandler = { err ->
            Log.e(TAG, "onErrorHandler: $err")
            initiatingSkywayContext = false
            skywayInitStateFlow.value = false
        }
        SkyWayContext.onTokenExpiredHandler = {
            Log.e(TAG, "onTokenExpiredHandler...")
            initiatingSkywayContext = false
            skywayInitStateFlow.value = false
            scope.launch {
                val result = SkyWayContext.updateAuthToken(getToken())
                skywayInitStateFlow.value = result
                initiatingSkywayContext = false
            }
        }

        SkyWayContext.onReconnectStartHandler = {
            initiatingSkywayContext = true
            skywayInitStateFlow.value = false
            Log.d(TAG, "onReconnectStartHandler")
        }

        SkyWayContext.onReconnectSuccessHandler = {
            initiatingSkywayContext = false
            skywayInitStateFlow.value = true
            Log.d(TAG, "onReconnectSuccessHandler")
        }

    }

    var initiatingSkywayContext = false

    private val skywayInitStateFlow = MutableStateFlow(false)

    suspend fun initSkywayContext(context: Context) : Boolean {
        initiatingSkywayContext = true
        skywayInitStateFlow.value = false
        val result = initSkywayContextInternal(context)
        Log.d(TAG, "init Skyway $result")
        skywayInitStateFlow.value = result
        initiatingSkywayContext = false
        return result
    }

    suspend fun reinitSkywayContext(context: Context) : Boolean {
        this.disposeSkywayContext()
        return this.initSkywayContext(context)
    }

    private suspend fun initSkywayContextInternal(context: Context): Boolean {
        if(SkyWayContext.isSetup){
            SkyWayContext.dispose()
        }
        val option = SkyWayContext.Options(
            logLevel = Logger.LogLevel.ERROR,
//            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        return SkyWayContext.setup(context, getToken(), option)
    }

    suspend fun ensureSkywayInit(context: Context, onInitSuccess: suspend () -> Unit) {
        if (skywayInitStateFlow.value) {
            onInitSuccess.invoke()
        } else {
            if (initiatingSkywayContext) {
                skywayInitStateFlow.filter {
                    it
                }.collect {
                    onInitSuccess.invoke()
                }
            } else {
                val isSuccess = initSkywayContextInternal(context)
                if (isSuccess) {
                    onInitSuccess.invoke()
                } else {
                    //error
//                    SkyWayContext.dispose()
//                    val tryResult = initSkywayContextInternal(context)
//                    Log.e(TAG, "Skyway setup failed. Try again... $tryResult")
//                    onInitSuccess.invoke()
                }
            }
        }
    }

    fun disposeSkywayContext() {
        skywayInitStateFlow.value = false
        initiatingSkywayContext = false
        scope.launch {
            SkyWayContext.dispose()
        }
    }

    private suspend fun getToken(): String {
        val tokenResult = authRepository.getToken()
        return if (tokenResult is SkywayAuthResult.SkywayAuthSuccessResult)
            tokenResult.token else ""
    }

    private suspend fun registerId(id: String): RegisterResponse? {
        return authRepository.register(id)
    }

}
