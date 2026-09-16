package com.gc.waravi.views.dialogs

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.gc.waravi.R
import com.gc.waravi.base.BaseDialogFragment
import com.gc.waravi.databinding.DialogFragmentAvatarBinding
import com.gc.waravi.skyway.call.ARG_CALL_ID
import com.gc.waravi.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.min


const val ARG_AVATAR_BITMAP = "avatar-src"
enum class AvatarSize{
    Small,
    Medium,
    Large
}

class AvatarDialogFragment : BaseDialogFragment() {
    private lateinit var binding : DialogFragmentAvatarBinding
    private var imageSrc : Bitmap? = null
    private var currentImageSrc : Bitmap? = null
    private var peerId : String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogFragmentAvatarBinding.inflate(inflater, container, false)
        initViews()
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imageSrc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_AVATAR_BITMAP, Bitmap::class.java)
            } else{
                it.getParcelable(ARG_AVATAR_BITMAP)
            }
            peerId = it.getString(ARG_CALL_ID)
        }
    }

    override fun onStart() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onStart()
    }

    private fun initViews() {
        binding.btnSet.setOnClickListener {
            currentImageSrc?.let {
                lifecycleScope.launch(Dispatchers.IO){
                    val fileName = String.format("%s.png", peerId ?: "unknown_${System.currentTimeMillis()}")
                    Utils.saveImageToLocal(requireContext(), it, fileName){
                        val isSuccess = !it.isNullOrEmpty()
                        lifecycleScope.launch(Dispatchers.Main){
                            Toast.makeText(requireContext(),
                                getString(if (isSuccess) R.string.msg_set_avatar_success else R.string.msg_something_wrong),
                                Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                }
            }
        }
        binding.btnBack.setOnClickListener {
            dismiss()
        }
        binding.rdgSize.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                binding.rdSmall.id -> showPreviewImage(AvatarSize.Large)
                binding.rdMedium.id -> showPreviewImage(AvatarSize.Medium)
                binding.rdLarge.id -> showPreviewImage(AvatarSize.Small)
            }
        }
        binding.rdMedium.requestFocus()
        binding.rdMedium.isChecked = true
    }

    private fun showPreviewImage(size: AvatarSize){
        val scale = when(size){
            AvatarSize.Small -> 0.6f
            AvatarSize.Medium -> 0.8f
            AvatarSize.Large -> 1f
        }
        if (currentImageSrc != null){
            currentImageSrc?.recycle()
        }
        currentImageSrc = getCroppedImage(imageSrc!!, scale)
        Glide.with(this)
            .load(currentImageSrc)
            .circleCrop()
            .into(binding.imvPreview)
    }

    private fun getCroppedImage(bitmap: Bitmap, scale: Float): Bitmap{
        val min = min(bitmap.width, bitmap.height)
        val squaredBitmap = ThumbnailUtils.extractThumbnail(bitmap, min, min)
        val scaledSize = (min - (min * scale)).toInt()
        val output = Bitmap.createBitmap(squaredBitmap, scaledSize/2, scaledSize/2,
            squaredBitmap.width - scaledSize, squaredBitmap.height - scaledSize)
        val roundedSize = min(min, 480)
        return ThumbnailUtils.extractThumbnail(output, roundedSize, roundedSize)
    }

    override fun onDestroy() {
        imageSrc?.recycle()
        currentImageSrc?.recycle()
        super.onDestroy()
    }

    companion object{
        @JvmStatic
        fun newInstance(peerId: String, avatarImage: Bitmap) =
            AvatarDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CALL_ID, peerId)
                    putParcelable(ARG_AVATAR_BITMAP, avatarImage)
                }
            }
    }
}