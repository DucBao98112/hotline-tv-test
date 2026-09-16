package com.gc.waravi.skyway

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.views.activities.HomeActivity

class HotlineAccessibilityService : AccessibilityService() {


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        if (!BaseApplication.isAppInForeground && action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_11
                || keyCode == KeyEvent.KEYCODE_CALL) {
                val intent = Intent(this, HomeActivity::class.java).apply {
                    setComponent(ComponentName(this@HotlineAccessibilityService.applicationContext, HomeActivity::class.java))
                    setAction(Intent.ACTION_MAIN)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}
}
