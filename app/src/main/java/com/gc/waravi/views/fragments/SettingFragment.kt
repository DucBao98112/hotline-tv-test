package com.gc.waravi.views.fragments

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.gc.waravi.BuildConfig
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentSettingBinding
import com.gc.waravi.views.activities.HomeActivity

class SettingFragment : BaseFragment<FragmentSettingBinding>() {
    private lateinit var settingOptionIds: List<View>

    override fun initViews() {
        if(BuildConfig.FLAVOR_product == "hotlineStore"){
            binding.btnUpdate.visibility = View.GONE
        }
        settingOptionIds = listOf(
            binding.btnBaseSetting,
            binding.btnCallSetting,
            binding.btnTos,
            binding.btnAbout,
            binding.btnUpdate,
            binding.btnRestart
        )

        binding.btnBack.setOnClickListener {
            activity?.onBackPressed()
        }

        settingOptionIds.forEach {
            it.setOnClickListener(onSettingButtonClickListener)
        }

        binding.btnFunc.setOnClickListener {
            if (binding.btnUpdate.isActivated){
                update()
            } else if (binding.btnRestart.isActivated){
                restart()
            }
        }

        binding.btnBaseSetting.performClick()
    }

    private fun findActivatedView(): View? {
        settingOptionIds.forEach {
            if (it.isActivated) return it
        }
        return null
    }

    private val onSettingButtonClickListener = View.OnClickListener { v ->
        updateSettingOptionState(v)
    }

    private fun updateSettingOptionState(activatedButton: View) {
        binding.tvSubtitle.text = (activatedButton as? TextView)?.text ?: ""
        settingOptionIds.forEach { button ->
            val isActivated = (activatedButton.id == button.id)
            button.isActivated = isActivated
        }
        binding.fragmentContainer.visibility =
            if (activatedButton == binding.btnUpdate || activatedButton == binding.btnRestart) View.GONE else View.VISIBLE
        binding.groupFunc.visibility = if (activatedButton == binding.btnUpdate || activatedButton == binding.btnRestart) View.VISIBLE else View.GONE
        when (activatedButton) {
            binding.btnBaseSetting -> {
                showChildFragment(BaseSettingFragment())
            }
            binding.btnCallSetting -> {
                showChildFragment(CallSettingFragment())
            }
            binding.btnTos -> {
                showChildFragment(TermOfServiceFragment())
            }
            binding.btnAbout -> {
                showChildFragment(AboutFragment())
            }
            binding.btnUpdate -> {
                binding.tvFuncMessage.text = getString(R.string.msg_update_confirmation)
                binding.btnFunc.text = getString(R.string.txt_run_update)
            }
            binding.btnRestart -> {
                binding.tvFuncMessage.text = getString(R.string.msg_restart_confirmation)
                binding.btnFunc.text = getString(R.string.txt_run_restart)
            }
            else -> {
                removeChildFragment()
            }
        }
    }

    private fun removeChildFragment() {
        childFragmentManager.popBackStackImmediate()
    }

    private fun showChildFragment(destination: Fragment) {
        childFragmentManager.beginTransaction().replace(R.id.fragment_container, destination)
            .commit()
    }

    private fun update(){
        (requireActivity() as HomeActivity).checkUpdate()
    }

    private fun restart() {
        (requireActivity() as HomeActivity).restart()
    }

    private fun showConfirmationAlert(msg: String, action: () -> Unit){
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(msg)
            .setPositiveButton(getString(R.string.btn_ok)
            ) { p0, p1 ->
                action()
                p0.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel)
            ) { p0, p1 -> p0.dismiss() }
        dialog.show().getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSettingBinding {
        return FragmentSettingBinding.inflate(inflater, container, false)
    }

}