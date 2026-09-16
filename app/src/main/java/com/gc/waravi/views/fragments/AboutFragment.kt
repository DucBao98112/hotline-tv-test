package com.gc.waravi.views.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gc.waravi.BuildConfig
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentAboutBinding

class AboutFragment : BaseFragment<FragmentAboutBinding>() {
    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAboutBinding {
        return FragmentAboutBinding.inflate(inflater, container, false)
    }

    override fun initViews() {
        binding.tvVersion.text = getString(R.string.lbl_version, BuildConfig.VERSION_NAME)
        binding.tvAbout.visibility = if(BuildConfig.FLAVOR_product == "hotline") View.VISIBLE else
            View.GONE
    }
}