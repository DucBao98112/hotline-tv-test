package com.gc.waravi.views.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentSplashBinding
import com.gc.waravi.models.CallMode
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.popservice.PopService
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Timer

/**
 * スプラッシュ画面
 */
const val ARG_MAKE_CALL_TYPE = "call-mode"
const val ARG_MAKE_CALL_ID = "call-id"

class SplashFragment : BaseFragment<FragmentSplashBinding>() {
    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSplashBinding {
        return FragmentSplashBinding.inflate(inflater, container, false)
    }

    private val onReady = PublishSubject.create<Boolean>()
    private val onRegisterFinished = PublishSubject.create<Int>()
//    private val onDataAvailable = PublishSubject.create<Boolean>()
//    private val authRepository by lazy {
//        AuthRepository()
//    }
    private val registerTimer by lazy {
        Timer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disposable.add(Observable.combineLatest(onReady, onRegisterFinished)
        { _, nav_id ->
            nav_id
        }
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ nav_id ->
                findNavController().navigate(nav_id, getData())
            }, { error ->
                Log.e(this::class.java.simpleName, error.message.toString())
            })
        )
    }

    private fun getData() : Bundle?{
        val bundle: Bundle = activity?.intent?.extras ?: return null
        val action = activity?.intent?.action
        Log.d(this::class.simpleName, "APP ACTIONS: $action")
        bundle.keySet().forEach { key ->
            Log.d(this::class.simpleName, "[$key=${bundle.get(key)}]")
        }
        var callMode : CallMode = CallMode.P2P
        val callId = if (bundle.containsKey("telephone")) {
            bundle.getString("telephone")
        } else if (bundle.containsKey("callId")){
            bundle.getString("callId")
        } else if(bundle.containsKey("roomId")){
            callMode = CallMode.Room
            bundle.getString("roomId")
        } else {
            ""
        }

        return if(!callId.isNullOrEmpty()) bundleOf(
            ARG_MAKE_CALL_TYPE to callMode,
            ARG_MAKE_CALL_ID to callId) else null
    }

    private fun startCallService(id : String, authKey: String) {
        lifecycleScope.launch(Dispatchers.IO){
            SkywayManager.selfId = id
            CallManager.registerForegroundCall(id)
            startAtPopService()
            val token = FirebaseMessaging.getInstance().token.await()
            DeviceManager.updateDeviceInfo(requireContext(), id, token, Utils.getDeviceId(requireContext()), authKey)
            withContext(Dispatchers.Main) {
                viewModel.setCurrentPeerId(id)
                onRegisterFinished.onNext(R.id.goDial)
            }
        }
    }

    private fun startAtPopService(){
        ContextCompat.startForegroundService(
            BaseApplication.get(),
            Intent(BaseApplication.get(), PopService::class.java)
        )
    }

    override fun initViews() {
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1000L)
            onReady.onNext(true)
        }
        registerWithLastId()
    }

    private fun registerWithLastId() {
        val lastId = PrefUtils.getLastId(context ?: requireContext())
        val authKey = PrefUtils.getAuthKey(context ?: requireContext())

        if (lastId.isNotEmpty() && authKey.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val isNetworkAvailable = Utils.isOnline()
                if (!isNetworkAvailable){
                    showSnackBar(R.string.msg_check_network)
                    return@launch
                }
                SkywayManager.initSkywayContext(requireContext())
                withContext(Dispatchers.Main){
                    startCallService(lastId, authKey)
//                    val setupResult = SkywayManager.setup(requireActivity())
//                    if (isNetworkAvailable && setupResult) {
//                        startCallService(lastId)
//                    } else {
//                        onRegisterFinished.onNext(R.id.register_id)
//                    }
                }
            }
        } else {
            onRegisterFinished.onNext(R.id.register_id)
        }
    }

    override fun onDestroy() {
        disposable.clear()
        registerTimer.cancel()
        super.onDestroy()
    }
}