package com.gc.waravi.base

import android.net.Uri
import android.view.View
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar

/**
 * ベースフラグメントの基本的なメソッド定義
 */
interface BaseFragmentImpl {
    fun showSnackBar(message: String, @Duration duration: Int = Snackbar.LENGTH_LONG)
    fun showSnackBar(@StringRes resId: Int, @Duration duration: Int = Snackbar.LENGTH_LONG)
    fun checkPermission(permission: String) : Boolean
    fun playSound(@RawRes rawIdRes: Int, isLoop: Boolean = false)
    fun playSound(uri: Uri, isLoop: Boolean = false)
    fun stopSound()
    fun requestAudioFocus()
    fun abandonAudioFocus()
    fun closeKeyboard(currentView: View)
}