package com.gc.waravi.views.dialogs

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.R
import com.gc.waravi.databinding.LayoutBackgroundSelectionBinding
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.utils.PrefUtils
import java.io.File
import java.io.FileOutputStream

class BackgroundSelectionBottomSheet : DialogFragment() {

    private var _binding: LayoutBackgroundSelectionBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "BGSelection"
    }

    /**
     * Danh sách nền ảo.
     *
     * path:
     * ""       = tắt nền ảo
     * assets/  = ảnh có sẵn trong app
     * CUSTOM   = ảnh người dùng chọn từ máy
     */
    private val backgrounds = listOf(
        BackgroundItem(
            label = "Off",
            path = "",
            resId = R.color.black
        ),
        BackgroundItem(
            label = "Office",
            path = "assets/backgrounds/bg_office.png",
            resId = 0
        ),
        BackgroundItem(
            label = "Office 2",
            path = "assets/backgrounds/bg_office_2.jpg",
            resId = 0
        ),
        BackgroundItem(
            label = "Hospital",
            path = "assets/backgrounds/bg_hospital.jpg",
            resId = 0
        ),
        BackgroundItem(
            label = "Tech",
            path = "assets/backgrounds/bg_tech.jpg",
            resId = 0
        ),
        BackgroundItem(
            label = "Minimal",
            path = "assets/backgrounds/bg_minimal.jpg",
            resId = 0
        ),
        BackgroundItem(
            label = "Bookshelf",
            path = "assets/backgrounds/bg_bookshelf.jpg",
            resId = 0
        ),
        BackgroundItem(
            label = "Custom",
            path = "CUSTOM",
            resId = R.drawable.ic_action_add
        )
    )

    private val pickImageLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }

            result.data?.data?.let { uri ->
                saveCustomBackground(uri)
            }
        }

    /**
     * Compact chooser anchored above the call controls at the bottom.
     */
    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setLayout(
                minOf(resources.getDimensionPixelSize(
                    R.dimen.virtual_background_panel_width
                ), resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                y = resources.getDimensionPixelSize(
                    R.dimen.virtual_background_panel_top_margin
                )
            }
        }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = LayoutBackgroundSelectionBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupBackgroundList()

        /*
         * Android TV:
         * Sau khi dialog mở, focus vào ảnh nền đầu tiên.
         */
        binding.rvBackgrounds.post {
            if (binding.rvBackgrounds.childCount > 0) {
                binding.rvBackgrounds.getChildAt(0)?.requestFocus()
            }
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private fun setupUI() {

        val context = requireContext()

        /*
         * Blur:
         * Nếu đang dùng Virtual Background thì lấy trạng thái
         * Virtual Background Blur.
         *
         * Nếu không dùng Virtual Background thì lấy Blur thường.
         */
        binding.swBlur.isChecked =
            if (PrefUtils.isVirtualBackgroundEnabled(context)) {
                PrefUtils.isVirtualBgBlurEnabled(context)
            } else {
                PrefUtils.isBlurEnabled(context)
            }

        binding.swBlur.setOnCheckedChangeListener { _, isChecked ->

            if (PrefUtils.isVirtualBackgroundEnabled(context)) {

                PrefUtils.saveVirtualBgBlurEnabled(
                    context,
                    isChecked
                )

            } else {

                PrefUtils.saveBlurEnabled(
                    context,
                    isChecked
                )
            }

            CallManager.updateVideoProcessors(context)
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupBackgroundList() {

        binding.rvBackgrounds.apply {

            // RecyclerView does not render its adapter without a LayoutManager.
            // The missing manager made the sheet appear empty even though the
            // built-in background list was already populated.
            layoutManager = LinearLayoutManager(
                context,
                RecyclerView.HORIZONTAL,
                false
            )

            adapter = BackgroundAdapter(
                backgrounds
            ) { item ->
                handleItemClick(item)
            }

            /*
             * Android TV:
             * Cho phép focus bằng remote.
             */
            isFocusable = true
            isFocusableInTouchMode = true

            descendantFocusability =
                ViewGroup.FOCUS_AFTER_DESCENDANTS

            setHasFixedSize(true)
        }
    }

    // ============================================================
    // BACKGROUND CLICK
    // ============================================================

    private fun handleItemClick(
        item: BackgroundItem
    ) {

        val context = requireContext()

        Log.d(
            TAG,
            "Selected background: ${item.label}, path=${item.path}"
        )

        /*
         * CUSTOM
         */
        if (item.path == "CUSTOM") {

            val intent = Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {
                type = "image/*"
                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
            }

            pickImageLauncher.launch(intent)

            return
        }

        /*
         * OFF
         */
        if (item.path.isEmpty()) {

            PrefUtils.saveVirtualBackgroundEnabled(
                context,
                false
            )

            PrefUtils.saveVirtualBackgroundImage(
                context,
                ""
            )

            Log.d(
                TAG,
                "Virtual background OFF"
            )

        } else {

            /*
             * ON
             */
            PrefUtils.saveVirtualBackgroundEnabled(
                context,
                true
            )

            /*
             * Lưu chính xác path của ảnh.
             */
            PrefUtils.saveVirtualBackgroundImage(
                context,
                item.path
            )

            /*
             * Giữ trạng thái Blur Background.
             */
            PrefUtils.saveVirtualBgBlurEnabled(
                context,
                binding.swBlur.isChecked
            )

            /*
             * Nếu bật Virtual Background,
             * tắt Blur thường để tránh chạy 2 processor cùng lúc.
             */
            PrefUtils.saveBlurEnabled(
                context,
                false
            )

            Log.d(
                TAG,
                "Virtual background ON: ${item.path}"
            )
        }

        /*
         * Cập nhật VideoProcessor.
         */
        CallManager.updateVideoProcessors(
            context
        )

        /*
         * Cập nhật viền selected.
         */
        binding.rvBackgrounds.adapter?.notifyDataSetChanged()
    }

    // ============================================================
    // CUSTOM IMAGE
    // ============================================================

    private fun saveCustomBackground(
        uri: Uri
    ) {

        val context = requireContext()

        try {

            val inputStream =
                context.contentResolver.openInputStream(uri)

            if (inputStream == null) {

                Log.e(
                    TAG,
                    "Cannot open selected image"
                )

                return
            }

            /*
             * Luôn lưu file mới.
             */
            val file = File(
                context.filesDir,
                "custom_background.jpg"
            )

            FileOutputStream(file).use { output ->

                inputStream.use { input ->

                    input.copyTo(output)
                }
            }

            /*
             * Kiểm tra file có decode được không.
             */
            val testBitmap =
                BitmapFactory.decodeFile(
                    file.absolutePath
                )

            if (testBitmap == null) {

                Log.e(
                    TAG,
                    "Custom background decode failed"
                )

                return
            }

            testBitmap.recycle()

            /*
             * Bật Virtual Background.
             */
            PrefUtils.saveVirtualBackgroundEnabled(
                context,
                true
            )

            /*
             * Lưu đường dẫn file.
             */
            PrefUtils.saveVirtualBackgroundImage(
                context,
                file.absolutePath
            )

            /*
             * Giữ trạng thái Blur Background.
             */
            PrefUtils.saveVirtualBgBlurEnabled(
                context,
                binding.swBlur.isChecked
            )

            /*
             * Tắt Blur thường.
             */
            PrefUtils.saveBlurEnabled(
                context,
                false
            )

            /*
             * Reload processor.
             */
            CallManager.updateVideoProcessors(
                context
            )

            /*
             * Refresh UI.
             */
            binding.rvBackgrounds.adapter?.notifyDataSetChanged()

            Log.d(
                TAG,
                "Custom background saved: ${file.absolutePath}"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error saving custom background",
                e
            )
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroyView() {

        binding.rvBackgrounds.adapter = null

        super.onDestroyView()

        _binding = null
    }

    // ============================================================
    // ADAPTER
    // ============================================================

    inner class BackgroundAdapter(
        private val items: List<BackgroundItem>,
        private val onItemClick: (BackgroundItem) -> Unit
    ) : RecyclerView.Adapter<BackgroundAdapter.ViewHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder {

            val view = LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_background_preset,
                    parent,
                    false
                )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int
        ) {

            val item = items[position]

            holder.bind(item)

            /*
             * Android TV remote:
             *
             * OK / ENTER
             * → chọn nền
             */
            holder.itemView.setOnKeyListener { _, keyCode, event ->

                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                    (
                            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER ||
                                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                            )
                ) {

                    onItemClick(item)

                    true

                } else {

                    false
                }
            }

            /*
             * Click chuột / touch.
             */
            holder.itemView.setOnClickListener {

                onItemClick(item)
            }

            /*
             * Khi focus bằng remote,
             * làm item nổi bật.
             */
            holder.itemView.setOnFocusChangeListener { view, hasFocus ->

                view.alpha =
                    if (hasFocus) {
                        1.0f
                    } else {
                        0.85f
                    }

                /*
                 * Scale nhẹ khi focus.
                 * Giúp người dùng biết đang chọn item nào.
                 */
                if (hasFocus) {

                    view.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(100)
                        .start()

                } else {

                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        // ========================================================
        // VIEW HOLDER
        // ========================================================

        inner class ViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(itemView) {

            private val ivPreset: ImageView =
                itemView.findViewById(
                    R.id.iv_preset
                )

            private val ivSelection: ImageView =
                itemView.findViewById(
                    R.id.iv_selection_indicator
                )

            private val tvLabel: TextView =
                itemView.findViewById(
                    R.id.tv_label
                )

            fun bind(
                item: BackgroundItem
            ) {

                val context = itemView.context

                tvLabel.text = item.label

                /*
                 * Current selected background.
                 */
                val currentPath =
                    PrefUtils.getVirtualBackgroundImage(
                        context
                    )

                val isEnabled =
                    PrefUtils.isVirtualBackgroundEnabled(
                        context
                    )

                /*
                 * Xác định item đang được chọn.
                 */
                val isSelected =
                    when {

                        /*
                         * OFF
                         */
                        item.path.isEmpty() -> {
                            !isEnabled
                        }

                        /*
                         * CUSTOM
                         */
                        item.path == "CUSTOM" -> {

                            isEnabled &&
                                    currentPath.isNotEmpty() &&
                                    currentPath != "assets/backgrounds/bg_office.png" &&
                                    currentPath != "assets/backgrounds/bg_office_2.jpg" &&
                                    currentPath != "assets/backgrounds/bg_hospital.jpg" &&
                                    currentPath != "assets/backgrounds/bg_tech.jpg" &&
                                    currentPath != "assets/backgrounds/bg_minimal.jpg" &&
                                    currentPath != "assets/backgrounds/bg_bookshelf.jpg"
                        }

                        /*
                         * Preset
                         */
                        else -> {

                            isEnabled &&
                                    currentPath == item.path
                        }
                    }

                /*
                 * Hiển thị dấu selected.
                 */
                ivSelection.visibility =
                    if (isSelected) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                /*
                 * ==================================================
                 * LOAD THUMBNAIL
                 * ==================================================
                 */

                if (item.path.isEmpty()) {

                    /*
                     * OFF
                     */
                    ivPreset.setImageResource(
                        R.color.black
                    )

                    ivPreset.scaleType =
                        ImageView.ScaleType.CENTER_CROP

                } else if (item.path == "CUSTOM") {

                    /*
                     * CUSTOM
                     */
                    ivPreset.setImageResource(
                        item.resId
                    )

                    ivPreset.scaleType =
                        ImageView.ScaleType.CENTER_INSIDE

                } else if (
                    item.path.startsWith("assets/")
                ) {

                    /*
                     * Asset image.
                     */
                    try {

                        context.assets
                            .open(
                                item.path.removePrefix(
                                    "assets/"
                                )
                            )
                            .use { stream ->

                                val bitmap =
                                    BitmapFactory.decodeStream(
                                        stream
                                    )

                                if (bitmap != null) {

                                    ivPreset.setImageBitmap(
                                        bitmap
                                    )

                                    ivPreset.scaleType =
                                        ImageView.ScaleType.CENTER_CROP

                                } else {

                                    ivPreset.setImageResource(
                                        R.color.chat_input_bg
                                    )
                                }
                            }

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Cannot load thumbnail: ${item.path}",
                            e
                        )

                        ivPreset.setImageResource(
                            R.color.chat_input_bg
                        )
                    }
                }

                /*
                 * Selection rõ hơn.
                 */
                if (isSelected) {

                    ivSelection.visibility =
                        View.VISIBLE

                    itemView.isSelected = true

                } else {

                    ivSelection.visibility =
                        View.GONE

                    itemView.isSelected = false
                }
            }
        }
    }

    // ============================================================
    // MODEL
    // ============================================================

    data class BackgroundItem(
        val label: String,
        val path: String,
        val resId: Int
    )
}
