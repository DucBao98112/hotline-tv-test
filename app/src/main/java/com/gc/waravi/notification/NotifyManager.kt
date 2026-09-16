package com.gc.waravi.notification

import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import com.gc.waravi.R
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import java.util.Timer

object NotifyManager {
    private var incomingDialog: Dialog? = null
    private var autoAnswerTimer: CountDownTimer? = null
    private var alerts = mutableMapOf<String, Dialog>()

    fun showIncomingLayout(
        context: Context, peerId: String, displayName: String? = null,
        onAccept: (() -> Unit), onDecline: (() -> Unit)
    ) {
        if (incomingDialog != null) {
            incomingDialog?.dismiss()
        }

        this.closeDialogAlertIfNeeded(peerId)

        val isAutoAnswerEnable = PrefUtils.isAutoAnsweringEnable(context)
        val popupSize = PrefUtils.getOverlayPopupSize(context)

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (dialog.window != null) {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val wlp: WindowManager.LayoutParams = dialog.window!!.attributes
            wlp.gravity = Gravity.BOTTOM
            wlp.windowAnimations = R.style.DialogBottomTheme
            dialog.window!!.attributes = wlp
        }

        dialog.setCancelable(false)
        val layoutId = when (popupSize) {
            2 -> R.layout.layout_incoming_dialog_medium
            3 -> R.layout.layout_incoming_dialog_large
            else -> R.layout.layout_incoming_dialog
        }
        dialog.setContentView(layoutId)
        val btnAccept = dialog.findViewById<View>(R.id.btn_accept_call)
        val btnDecline = dialog.findViewById<View>(R.id.btn_decline_call)
        val btnCancel = dialog.findViewById<View>(R.id.btn_cancel)
        val tvMessage = dialog.findViewById<TextView>(R.id.tv_message)
//        val controlGroup = dialog.findViewById<Group>(R.id.group_call_control)
//        controlGroup.visibility = if (isAutoAnswerEnable) View.GONE else View.VISIBLE
        val tvId = dialog.findViewById<TextView>(R.id.tv_id)
        tvId.text = displayName ?: peerId
        btnAccept.setOnClickListener {
            autoAnswerTimer?.cancel()
            onAccept()
            dialog.dismiss()
            incomingDialog = null
        }
        btnDecline.setOnClickListener {
            autoAnswerTimer?.cancel()
            onDecline()
            dialog.dismiss()
            incomingDialog = null
        }
        btnCancel.setOnClickListener {
            autoAnswerTimer?.cancel()
            onDecline()
            dialog.dismiss()
            incomingDialog = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.window?.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }
        dialog.show()
        if (isAutoAnswerEnable) {
            val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
            val countdownTime = if(powerManager?.isInteractive == false) 21000L else 6000L
//            btnCancel.visibility = View.VISIBLE
            tvMessage.text = context.getString(R.string.msg_auto_answering, (countdownTime/1000) - 1)
            autoAnswerTimer = object : CountDownTimer(countdownTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val tick = (millisUntilFinished / 1000).toInt()
                    tvMessage.text = context.getString(R.string.msg_auto_answering, tick)
                }

                override fun onFinish() {
                    onAccept()
                    dialog.dismiss()
                    incomingDialog = null
                }
            }
            autoAnswerTimer!!.start()
        }
        incomingDialog = dialog
    }

    fun closeIncomingLayout() {
        if (incomingDialog != null && incomingDialog!!.isShowing) {
            incomingDialog?.dismiss()
        }
        autoAnswerTimer?.cancel()
    }

    fun showNotificationAlert(context: Context, id: String, message: String, onButtonClicked: (() -> Unit)? = null){
        this.closeDialogAlertIfNeeded(id)
        Utils.playKeyTone(ToneGenerator.TONE_CDMA_ABBR_ALERT)

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (dialog.window != null) {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val wlp: WindowManager.LayoutParams = dialog.window!!.attributes
            wlp.gravity = Gravity.TOP or Gravity.START
            wlp.width = Utils.dpToPx(context, 620)
            wlp.windowAnimations = R.style.DialogBottomTheme
            dialog.window!!.attributes = wlp
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.window?.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }

        dialog.setContentView(R.layout.layout_notification_alert)
        val btnOpen = dialog.findViewById<View>(R.id.btn_open)
        val btnClose = dialog.findViewById<View>(R.id.btn_close)
        val tvMessage = dialog.findViewById<TextView>(R.id.tv_message)
        tvMessage.text = message
        btnOpen.visibility = if (onButtonClicked != null) View.VISIBLE else View.GONE
        btnOpen.setOnClickListener {
            onButtonClicked?.invoke()
            dialog.dismiss()
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        alerts[id] = dialog
        dialog.show()
    }

    fun closeDialogAlertIfNeeded(alertId: String){
        Log.e("NQD", "closeDialogAlertIfNeeded... $alertId")
        alerts.keys.forEach { k -> Log.e("NQD", "alert... $k") }
        alerts[alertId]?.dismiss()
    }

}
