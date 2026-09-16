package com.gc.waravi.base

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.fragment.app.DialogFragment

abstract class BaseDialogFragment : DialogFragment(){
    open fun onBackPress() : Boolean{
        return false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnKeyListener { _, keyCode, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_PROG_RED)) {
                    return@setOnKeyListener onBackPress()
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onStart() {
        dialog?.window?.let { window ->
            val backgroundColor = ColorDrawable(Color.BLACK)
            backgroundColor.alpha = 75
            window.setBackgroundDrawable(backgroundColor)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        super.onStart()
    }
}