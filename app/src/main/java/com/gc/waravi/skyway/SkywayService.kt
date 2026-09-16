package com.gc.waravi.skyway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gc.waravi.R
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.call.SocketEvent
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.activities.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

/**
 *　アプリケーションが非アクティブのときに着信コールを受信するフォアグラウンドサービス
 */
class SkywayService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var callEventJob: Job? = null
    private val connectionCheckingTimer = Timer()

    override fun onCreate() {
        showForegroundNotification()
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val lastId = PrefUtils.getLastId(this)
        if (lastId.isNotEmpty()){
            this.connectSocket(lastId)
        }
        this.startConnectionChecking()
        return START_REDELIVER_INTENT
    }

    private fun connectSocket(id: String){
        if (callEventJob?.isActive == true) {
            callEventJob?.cancel()
        }
        if (SocketManager.isConnected()) {
            SocketManager.disconnectSocket()
        }
        SocketManager.connectSocket(id)
        SocketManager.socketEvents.let { sharedFlow ->
            callEventJob = scope.launch {
                sharedFlow.collect { event ->
                    when (event) {
                        is SocketEvent.IncomingCall -> {
                            CallManager.onSocketIncomingCall(event.data)
                        }

                        is SocketEvent.EndCall -> {
                            val data = event.data
                            CallManager.onSocketIncomingCallEnded(data.type)
                        }
                    }
                }
            }
        }
    }

    private fun startConnectionChecking(){
        //start connection checking
        connectionCheckingTimer.scheduleAtFixedRate(3 * 1000L, CONNECTION_CHECKING_PERIOD){
            Log.e("NQD", "Socket connection: ${SocketManager.isConnected()}")
            if (SocketManager.isConnected().not()){
                tryReconnectSocket()
            }
        }
    }

    private fun tryReconnectSocket(){
        val lastId = PrefUtils.getLastId(this)
        if (lastId.isEmpty()) return
        Log.e(this::class.simpleName, "tryReconnectSocket...")
        SocketManager.disconnectSocket()
        this.connectSocket(lastId)
    }

    private fun showForegroundNotification() {
        val lastId = PrefUtils.getLastId(this)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            startForeground(1001, createNotification(lastId), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else{
            startForeground(1001, createNotification(lastId))
        }
    }

//    private fun updateNotification(peerId: String) {
//        val notificationMgr = NotificationManagerCompat.from(this)
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
//            if (ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.POST_NOTIFICATIONS
//                ) == PackageManager.PERMISSION_GRANTED
//            ) {
//                notificationMgr.notify(1001, createNotification(peerId))
//            }
//        } else{
//            notificationMgr.notify(1001, createNotification(peerId))
//        }
//    }

    private fun createNotification(peerId: String? = null): Notification {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
            else -> FLAG_UPDATE_CURRENT
        }

        val builder = NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.mipmap.ic_launcher_round
                )
            )
            .setContentTitle(getString(R.string.notification_title, peerId ?: ""))
            .setContentText(
                getString(
                    R.string.notification_description,
                    getString(R.string.app_name)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(this, 0, intent, flags))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFY_CHANNEL_ID,
                String.format("%s Call Service", getString(R.string.app_name)),
                NotificationManager.IMPORTANCE_HIGH
            )
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
        return builder.build()
    }

    override fun onDestroy() {
        if (callEventJob?.isActive == true) {
            callEventJob?.cancel()
        }
        connectionCheckingTimer.cancel()
        SocketManager.disconnectSocket()
        Log.e(this::class.simpleName, "Service is destroy...")
        super.onDestroy()
    }

    companion object {
        const val NOTIFY_CHANNEL_ID = "hotline_channel"
        private const val CONNECTION_CHECKING_PERIOD = 10 * 60 * 1000L
    }
}