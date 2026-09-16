package com.gc.waravi.views.fragments

import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentCallSettingBinding
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.activities.HomeActivity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CallSettingFragment : BaseFragment<FragmentCallSettingBinding>() {

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCallSettingBinding {
        return FragmentCallSettingBinding.inflate(inflater, container, false)
    }

    override fun initViews() {
        binding.btnOk.setOnClickListener {
            binding.btnOk.clearFocus()
        }
        binding.btnClearHistory.setOnClickListener {
            val dialFragment = ClearDataFragment.newInstance(DataType.RECENT)
            dialFragment.show(childFragmentManager, "recent-dialog")
        }
        binding.btnClearShort.setOnClickListener {
            val dialFragment = ClearDataFragment.newInstance(DataType.SHORT)
            dialFragment.show(childFragmentManager, "short-dialog")
        }
        //init overlay setting
        val isOverlayEnable =
            PrefUtils.isOverlayEnable(requireContext()) and Settings.canDrawOverlays(requireContext())
        binding.swOverlay.isChecked = isOverlayEnable
        binding.swOverlay.text = getString(if (isOverlayEnable) R.string.lbl_on else R.string.lbl_off)
        binding.swOverlay.setOnCheckedChangeListener { view, isChecked ->
            view.text = getString(if (isChecked) R.string.lbl_on else R.string.lbl_off)
            if (isChecked) {
                PrefUtils.setOverlayEnable(requireContext(), true)
                if (!Settings.canDrawOverlays(requireContext())) {
                    if (requireActivity() is HomeActivity) {
                        (requireActivity() as HomeActivity).showSettingDialog(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            getString(
                                R.string.permission_overlay_message,
                                getString(R.string.app_name)
                            )
                        )
                    }
                }
            } else {
                PrefUtils.setOverlayEnable(requireContext(), false)
            }
        }

        //init auto-answering state
        val isAutoAnsweringEnable = PrefUtils.isAutoAnsweringEnable(requireContext())
        binding.swAutoAnswering.isChecked = isAutoAnsweringEnable
        binding.swAutoAnswering.text = getString(if (isAutoAnsweringEnable) R.string.lbl_on else R.string.lbl_off)
        binding.swAutoAnswering.setOnCheckedChangeListener { view, isChecked ->
            view.text = getString(if (isChecked) R.string.lbl_on else R.string.lbl_off)
            PrefUtils.setAutoAnsweringEnable(requireContext(), isChecked)
        }

        //init overlay popup size setting
        val popupSize = PrefUtils.getOverlayPopupSize(requireContext())
        binding.rdOverlaySmall.isChecked = popupSize == 1
        binding.rdOverlayMedium.isChecked = popupSize == 2
        binding.rdOverlayLarge.isChecked = popupSize == 3
        binding.rdgOverlaySize.setOnCheckedChangeListener { _, checkedId ->
            val value = if (checkedId == binding.rdOverlayMedium.id) 2
            else if (checkedId == binding.rdOverlayLarge.id) 3 else 1
            PrefUtils.setOverlayPopupSize(requireContext(), value)
        }

        val isReductionOn = PrefUtils.getLoadReductionSetting(requireContext())
        binding.swReduceSystem.isChecked = isReductionOn
        binding.swReduceSystem.setOnCheckedChangeListener { _, isChecked ->
            PrefUtils.saveLoadReductionSetting(requireContext(), isChecked)

            //update device info
            lifecycleScope.launch(Dispatchers.IO) {
                val token = FirebaseMessaging.getInstance().token.await()
                val id = PrefUtils.getLastId(requireContext())
                val authKey = PrefUtils.getAuthKey(requireContext())
                DeviceManager.updateDeviceInfo(requireContext(), id, token, Utils.getDeviceId(requireContext()), authKey, isChecked)
            }
        }

        initCameraSetting()
        initMirrorSetting()
        initBackgroundProcessingSetting()
    }

    private fun initBackgroundProcessingSetting() {
        binding.swBlur.setOnClickListener {
            showBackgroundSelection()
        }
        binding.swVirtualBg.setOnClickListener {
            showBackgroundSelection()
        }
        binding.btnBgOffice.setOnClickListener {
            showBackgroundSelection()
        }
        binding.btnBgBookshelf.setOnClickListener {
            showBackgroundSelection()
        }
        updateSelectedBackgroundUI()
    }

    private fun showBackgroundSelection() {
        val bottomSheet = com.gc.waravi.views.dialogs.BackgroundSelectionBottomSheet()
        bottomSheet.show(childFragmentManager, "background_selection")
    }

    private fun updateSelectedBackgroundUI() {
        val currentBg = PrefUtils.getVirtualBackgroundImage(requireContext())
        binding.btnBgOffice.isActivated = currentBg == "assets/backgrounds/bg_office.png"
        binding.btnBgBookshelf.isActivated = currentBg == "assets/backgrounds/bg_bookshelf.jpg"
    }

    private fun initMirrorSetting() {
        val isMirror = PrefUtils.getVideoMirror(requireContext())
        binding.swVideoMirror.isChecked = isMirror
        binding.swVideoMirror.setOnCheckedChangeListener { _, isChecked ->
            PrefUtils.saveVideoMirror(requireContext(), isChecked)
        }
    }

    private fun initCameraSetting() {
        // Camera state setting
        binding.rdgCameraState.setOnCheckedChangeListener { _, id ->
            val cameraState = when (id) {
                binding.rdCameraState0.id -> 0
                binding.rdCameraState1.id -> 1
                binding.rdCameraState2.id -> 2
                else -> -1
            }
            PrefUtils.setCameraSettingState(requireContext(), cameraState)
        }
        //Init from saved state
        val initCameraState = when (PrefUtils.getCameraSettingState(requireContext())) {
            0 -> binding.rdCameraState0.id
            1 -> binding.rdCameraState1.id
            2 -> binding.rdCameraState2.id
            else -> binding.rdCameraState0.id
        }
        binding.rdgCameraState.check(initCameraState)

        //Camera api source setting
        val savedCameraSource = when (PrefUtils.getCameraSourceSetting(requireContext())) {
            CAMERA_SOURCE_ANDROID -> binding.rdCameraSourceAndroid.id
            CAMERA_SOURCE_USB -> binding.rdCameraSourceUsb.id
            else -> binding.rdCameraSourceAndroid.id
        }
        binding.rdgCameraApiSource.check(savedCameraSource)

        binding.rdgCameraApiSource.setOnCheckedChangeListener { _, id ->
            val cameraSource = when (id) {
                binding.rdCameraSourceAndroid.id -> CAMERA_SOURCE_ANDROID
                binding.rdCameraSourceUsb.id -> CAMERA_SOURCE_USB
                else -> 0
            }
            PrefUtils.setCameraSourceSetting(requireContext(), cameraSource)
        }
    }

    companion object{
        const val CAMERA_SOURCE_ANDROID = 1
        const val CAMERA_SOURCE_USB = 2
    }

}