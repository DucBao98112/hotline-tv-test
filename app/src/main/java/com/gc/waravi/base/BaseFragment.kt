package com.gc.waravi.base

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.views.viewmodels.MainViewModel
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers


/**
 * すべてのフラグメントの基本クラス
 */

abstract class BaseFragment<T : ViewBinding> : Fragment(), BaseFragmentImpl{
    lateinit var binding: T
    abstract fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): T
    abstract fun initViews()
    protected open fun onKeyEvent(keyEvent: KeyEvent) {}
    protected open fun onLongKeyEvent(keyEvent: KeyEvent){}
    val viewModel : MainViewModel by activityViewModels()
    val disposable by lazy {
        CompositeDisposable()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = onCreateViewBinding(inflater, container)
        initViews()
        return binding.root
    }

    override fun showSnackBar(message: String, @Duration duration: Int) {
        if(activity is BaseActivity){
            (activity as BaseActivity).showSnackBar(message, duration)
        } else{
            Toast.makeText(context, message, duration).show()
        }
    }

    override fun showSnackBar(@StringRes resId: Int, @Duration duration: Int) {
        if(activity is BaseActivity){
            (activity as BaseActivity).showSnackBar(getString(resId), duration)
        } else{
            Toast.makeText(context, getString(resId), duration).show()
        }
    }

    override fun checkPermission(permission: String): Boolean {
        context?.let {
            return ActivityCompat.checkSelfPermission(
                it,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                it,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    override fun playSound(@RawRes rawIdRes: Int, isLoop: Boolean) {
        if(activity is BaseActivity){
            (activity as BaseActivity).playSound(rawIdRes, isLoop)
        }
    }

    override fun playSound(uri: Uri, isLoop: Boolean) {
        if(activity is BaseActivity){
            (activity as BaseActivity).playSound(uri, isLoop)
        }
    }

    override fun stopSound() {
        if(activity is BaseActivity){
            (activity as BaseActivity).stopSound()
        }
    }

    override fun requestAudioFocus() {
        if(activity is BaseActivity){
            (activity as BaseActivity).requestAudioFocus()
        }
    }

    override fun abandonAudioFocus() {
        if(activity is BaseActivity){
            (activity as BaseActivity).abandonAudioFocus()
        }
    }

    override fun closeKeyboard(currentView: View) {
        val imm = getSystemService(requireActivity(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(currentView.windowToken, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.registerKeyEvent()
    }

    override fun onDestroy() {
        disposable.dispose()
        disposable.clear()
        super.onDestroy()
    }

    private fun registerKeyEvent(){
        if(activity is BaseActivity){
            disposable.add((activity as BaseActivity).onKeyEvent.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe (
                    {event ->
                        val holdTime = event.eventTime - event.downTime
                        if (event.action == KeyEvent.ACTION_UP && (event.eventTime - event.downTime) < 700){
                            onKeyEvent(event)
                        } else if(event.action == KeyEvent.ACTION_DOWN && holdTime > 700 && holdTime < 800){
                            onLongKeyEvent(event)
                        }
                    },{
                        Log.e(this::class.simpleName, it.localizedMessage)
                    }
                ))
        }
    }

    private fun unregisterKeyEvent(){
        if (activity is BaseActivity) {
            disposable.dispose()
        }
    }

}