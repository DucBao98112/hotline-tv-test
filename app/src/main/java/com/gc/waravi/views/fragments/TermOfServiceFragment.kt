package com.gc.waravi.views.fragments

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentTermOfServiceBinding
import com.gc.waravi.utils.Utils

class TermOfServiceFragment : BaseFragment<FragmentTermOfServiceBinding>() {

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTermOfServiceBinding {
        return FragmentTermOfServiceBinding.inflate(inflater, container, false)
    }

    override fun initViews() {
        binding.webView.setBackgroundColor(Color.TRANSPARENT)
        binding.webView.setOnFocusChangeListener { v, hasFocus ->
            binding.webViewFrame.isActivated = hasFocus
        }
        if(Utils.getSystemNightMode(requireContext()) == AppCompatDelegate.MODE_NIGHT_YES){
            binding.webView.loadUrl("file:///android_res/raw/term_of_service_dark.html")
        } else{
            binding.webView.loadUrl("file:///android_res/raw/term_of_service.html")
        }
    }

}