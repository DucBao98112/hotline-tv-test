package com.gc.waravi.views.activities

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.gc.waravi.R
import com.gc.waravi.base.BaseActivity
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.databinding.DialogDontShowAgainBinding
import com.gc.waravi.skyway.HotlineAccessibilityService
import com.gc.waravi.skyway.call.ARG_CALL_ID
import com.gc.waravi.skyway.call.CallEvent
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.utils.DownloadController
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.fragments.CallSettingFragment
import com.gc.waravi.views.viewmodels.MainViewModelFactory
import com.google.android.material.snackbar.BaseTransientBottomBar
import kotlinx.coroutines.launch

/**
 * ホームアクティビティ
 */

class HomeActivity : BaseActivity() {
    private val WRITE_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_home) as NavHostFragment)
        val inflater = navHostFragment.navController.navInflater
        val graph = inflater.inflate(R.navigation.nav_graph)
        navHostFragment.navController.graph = graph

        //check permission
        checkPermission()
        subscribeToCallEvents()

        //Set default camera source for google streamer
        val isRunningOnGoogleStreamer = Build.MODEL.contains("Google TV Streamer")
        if (PrefUtils.getCameraSourceSetting(this) == 0){
            PrefUtils.setCameraSourceSetting(
                this, if (isRunningOnGoogleStreamer) CallSettingFragment.CAMERA_SOURCE_USB
                else CallSettingFragment.CAMERA_SOURCE_ANDROID
            )
        }
    }

    private fun subscribeToCallEvents() {
        CallManager.callEvents.let { sharedFlow ->
            lifecycleScope.launch {
                sharedFlow.collect { observeCallEvents(it) }
            }
        }
    }

    private fun observeCallEvents(callEvent: CallEvent) {
        when (callEvent) {
            is CallEvent.IncomingCall -> {
                if (BaseApplication.isAppInForeground) {
                    findNavController(R.id.nav_host_fragment_content_home).navigate(
                        R.id.IncomingCall, bundleOf(
                            ARG_CALL_ID to callEvent.callerId
                        )
                    )
                } else {
                    lifecycleScope.launch {
                        val contact =
                            (application as BaseApplication).repository.findContact(callEvent.callerId)
                        val displayName = contact?.name ?: callEvent.callerId
                        CallManager.showIncomingPopup(
                            this@HomeActivity,
                            callEvent.callerId,
                            displayName
                        )
                    }
                }
            }

            else -> {

            }
        }
    }

    fun checkAccessibility() {
        val ignoreShowSetting = PrefUtils.getAccessibilitySetting(this)
        if (isAccessibilityServiceEnabled().not() && ignoreShowSetting.not()) {
            val dialogViewBinding = DialogDontShowAgainBinding.inflate(layoutInflater)
            dialogViewBinding.tvMessage.text =
                String.format(
                    getString(R.string.msg_accessibility_required),
                    getString(R.string.app_name)
                )
            dialogViewBinding.cbDontShowAgain.setOnCheckedChangeListener { _, isChecked ->
                PrefUtils.saveAccessibilitySetting(this, isChecked)
            }
            val permissionDialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogViewBinding.root)
                .setPositiveButton(getString(R.string.btn_ok)) { dialog, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (ex: ActivityNotFoundException) {
                        val intent = Intent(
                            Settings.ACTION_SETTINGS
                        )
                        startActivity(intent)
                    } finally {
                        dialog.dismiss()
                    }
                }.setNegativeButton(getString(R.string.btn_close)) { dialog, _ ->
                    dialog.dismiss()
                }.create()
            permissionDialog.show()
            permissionDialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, HotlineAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            ComponentName.unflattenFromString(it) == expectedComponent
        }
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_DENIED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_DENIED
        ) {
            val requestPermission =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    results.keys.forEach { key ->
                        if (results[key] == false) {
                            Log.w(getString(R.string.app_name), key)
                            showSettingDialog(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "通話を行うにはカメラやマイクへのアクセスを許可してください。"
                            )
                        }
                    }
                }
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }

        if (!Settings.canDrawOverlays(this)) {
            showSettingDialog(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                getString(R.string.permission_overlay_message, getString(R.string.app_name))
            )
        }

    }

    fun showSettingDialog(settingAction: String, message: String) {
        val dialog = Utils.createDialog(
            this,
            getString(R.string.permission_dialog_title),
            message,
            getString(R.string.btn_ok)
        ) {
            try {
                val intent = Intent(settingAction)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                val intent = Intent(
                    Settings.ACTION_SETTINGS
                )
                startActivity(intent)
            }
        }
        dialog.show().getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
    }

    private fun turnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = MainViewModelFactory((application as BaseApplication).repository)

    override fun onPause() {
        PrefUtils.setCurrentHomeState(this, false)
        super.onPause()
    }

    override fun onResume() {
        PrefUtils.setCurrentHomeState(this, true)
        super.onResume()
    }

    override fun onStart() {
        super.onStart()
        // Disable Sleep and Screen Lock
        val wnd = window
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        turnScreenOn()
    }

    override fun onStop() {
        // Enable Sleep and Screen Lock
//        turnScreenOn()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        MediaManager.stopMedia()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_PERMISSION_CODE) {
            // Request for camera permission.
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // start downloading
                DownloadController.enqueueDownload(this)
            } else {
                // Permission request was denied.
                this.showSnackBar(
                    getString(R.string.storage_permission_denied),
                    BaseTransientBottomBar.LENGTH_SHORT
                )
            }
        }
    }

    fun checkUpdate() {
        //Check if the storage permission has been granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // start downloading
            DownloadController.enqueueDownload(this)
        } else {
            // Permission is missing and must be requested.
            requestStoragePermission()
        }
    }

    fun setLocale(languageCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun restart() {
        //restart activity
        val intent = Intent(this, HomeActivity::class.java)
        val mPendingIntentId = 123456
        val mPendingIntent = PendingIntent.getActivity(
            this,
            mPendingIntentId,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent)
        finishAffinity()
    }

    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            val dialog = Utils.createDialog(
                this, getString(R.string.permission_dialog_title),
                getString(R.string.storage_access_required), getString(R.string.btn_ok)
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    WRITE_PERMISSION_CODE
                )
            }
            dialog.show().getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_PERMISSION_CODE
            )
        }
    }
}