package com.gc.waravi.skyway.popservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gc.waravi.BuildConfig
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.notification.NotifyManager
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.SkywayService
import com.gc.waravi.skyway.SkywayService.Companion.NOTIFY_CHANNEL_ID
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.skyway.popservice.models.EncryptionType
import com.gc.waravi.skyway.popservice.models.MailServerInfo
import com.gc.waravi.skyway.popservice.models.NotificationHeader
import com.gc.waravi.skyway.popservice.atpop.MailClient
import com.gc.waravi.utils.Constant
import com.sun.mail.imap.IMAPFolder
import jakarta.mail.*
import jakarta.mail.event.MessageCountAdapter
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.*
import java.util.*

class PopService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isStarted: Boolean = false
    private val mailClient = MailClient()
    private var mWakeLock : PowerManager.WakeLock? = null
    private var listenerTimeoutJob: Job? = null
    private var currentMailFolder: Folder? = null
    private var currentThread : Thread? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        startForegroundService()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.v(
            "BuildInformation",
            Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE + " " + Build.VERSION.INCREMENTAL + " "
        );
        val defaultMailServer = MailServerInfo(
            Constant.MailService.DEFAULT_MAIL_HOST,
            Constant.MailService.DEFAULT_MAIL_ACCOUNT,
            Constant.MailService.DEFAULT_MAIL_PASSWORD,
            993,
            EncryptionType.Ssl
        )
        this.stopNotificationListener()
        this.startNotificationListener(defaultMailServer)
        return START_REDELIVER_INTENT
    }

    private fun startNotificationListener(mailServerInfo: MailServerInfo) {
        val properties = Properties()
        properties["mail.imaps.starttls.enable"] =
            (mailServerInfo.encryption == EncryptionType.Tls)
        properties["mail.imaps.ssl.enable"] = (mailServerInfo.encryption == EncryptionType.Ssl)
        properties.put("mail.imaps.usesocketchannels", "true")

        properties["mail.debug"] = BuildConfig.DEBUG
        if (BuildConfig.DEBUG) {
            properties["mail.imaps.ssl.checkserveridentity"] = false
            properties["mail.imaps.ssl.trust"] = "*"
        }
        // Session.getDefaultInstance()は引数を変えて2回目以降に呼び出すと無視されるので使用しない
        val emailSession = Session.getInstance(properties)

        val protocol = when (mailServerInfo.encryption) {
            EncryptionType.None -> "imap"
            else -> "imaps"
        }
        val store = emailSession.getStore(protocol)

        listenerTimeoutJob = scope.launch(Dispatchers.IO) {
            with(mailServerInfo) {
                store.connect(host, port, user, password)
            }
            currentMailFolder = store.getFolder("INBOX")
            currentMailFolder?.open(Folder.READ_ONLY)
            Log.d(this::class.java.simpleName, "Mail folder is open: ${currentMailFolder?.isOpen}")

            var messageCount = currentMailFolder?.messages?.size ?: 0
            currentMailFolder?.addMessageCountListener(object : MessageCountAdapter() {
                override fun messagesAdded(ev: MessageCountEvent) {
                    Log.d(this::class.java.simpleName, "messagesAdded")
                    val newMessageCount = ev.messages.size
                    val fetchMessageCountStart = messageCount + 1
                    val fetchMessageCountEnd = messageCount + newMessageCount
                    messageCount = fetchMessageCountEnd
                    scope.launch(Dispatchers.IO) {
                        val notifications: List<NotificationHeader> =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                mailClient.fetchInRange(
                                    mailServerInfo,
                                    fetchMessageCountStart,
                                    fetchMessageCountEnd
                                )
                            } else {
                                ev.messages.map { message ->
                                    (message as MimeMessage).toNotificationHeader(
                                        ev.source as IMAPFolder,
                                        mailBoxId = mailServerInfo.user
                                    )
                                }
                            }
                        Log.d(this::class.java.simpleName, "Count: ${notifications.size}")
                        messageCount += newMessageCount
                        mWakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                            newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "PopService::MyWakelockTag"
                            ).apply {
                                acquire(10000L)
                            }
                        }
                        for (notification in notifications) {
                            val subject = notification.subject
                            val subjectParts = subject.split("|")
                            if (subjectParts.size == 3){
                                val type = subjectParts[0]
                                val callerId = subjectParts[1]
                                val calleeId = subjectParts[2]
                                if (type == "VOIP" && calleeId == SkywayManager.selfId){
                                    this@PopService.checkAndStartCallService()
                                    val contact = BaseApplication.get().repository.findContact(callerId)
                                    withContext(Dispatchers.Main) {
                                        NotifyManager.showNotificationAlert(
                                            this@PopService, callerId,
                                            String.format(
                                                ContextCompat.getString(
                                                    this@PopService,
                                                    R.string.msg_missed_call
                                                ), contact?.name ?: callerId
                                            )
                                        )
                                    }
                                }
                            } else{
                                withContext(Dispatchers.Main) {
                                    NotifyManager.showNotificationAlert(this@PopService, notification.messageID, notification.title){
                                        val intent = Intent(this@PopService, HomeActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        ContextCompat.startActivity(this@PopService, intent, null)
                                    }
                                }
                            }
                        }
                    }
                }
            })
            val runnable = ImapIdleRunnable(
                currentMailFolder as IMAPFolder,
                isSupported = (Build.VERSION.SDK_INT > Build.VERSION_CODES.N)
            )
            runnable.onErrorHandler = { exception ->
                if (exception is FolderClosedException){
                    stopNotificationListener()
                    startNotificationListener(mailServerInfo)
                }
            }
            currentThread = Thread(runnable)
            currentThread?.start()
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                delay(6 * 60 * 60 * 1000L)
            } else {
                delay(60 * 1000L)
            }
            //remove this listener
            runnable.onErrorHandler = null
            stopNotificationListener()
            startNotificationListener(mailServerInfo)
            isStarted = true
        }
    }

    private fun stopNotificationListener(){
        scope.launch(Dispatchers.IO) {
            try {
                listenerTimeoutJob?.cancel()
                if (currentMailFolder?.isOpen == true){
                    currentMailFolder?.close()
                    currentMailFolder = null
                }
                currentThread?.interrupt()
            } catch (ex: IllegalStateException){
                ex.printStackTrace()
            }

        }
    }

    private fun startForegroundService(peerId: String? = null) {
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

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            startForeground(1001, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else{
            startForeground(1001, builder.build())
        }
    }

    private fun checkAndStartCallService(){
        Log.e("NQD", "checkAndStartCallService...")
        val intent = Intent(this, SkywayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        this.stopNotificationListener()
        mWakeLock?.release()
        scope.cancel()
        super.onDestroy()
    }

}

