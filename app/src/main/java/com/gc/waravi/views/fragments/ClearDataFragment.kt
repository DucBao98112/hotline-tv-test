package com.gc.waravi.views.fragments

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.R
import com.gc.waravi.base.BaseDialogFragment
import com.gc.waravi.databinding.FragmentClearDataBinding
import com.gc.waravi.views.adapters.RecentAdapter
import com.gc.waravi.views.adapters.ShortAdapter
import com.gc.waravi.views.adapters.ShortDisplayType
import com.gc.waravi.views.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARG_CLEAR_DATA_TYPE = "data-type"

enum class DataType{
    RECENT, SHORT
}

class ClearDataFragment : BaseDialogFragment() {
    private var dataType: DataType? = null
    private lateinit var binding : FragmentClearDataBinding
    private var recentAdapter : RecentAdapter? = null
    private var shortAdapter : ShortAdapter? = null
    private val mainViewModel : MainViewModel by activityViewModels()

    override fun onStart() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                dataType = it.getSerializable(ARG_CLEAR_DATA_TYPE, DataType::class.java)
            } else{
                dataType= it.getSerializable(ARG_CLEAR_DATA_TYPE) as? DataType
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentClearDataBinding.inflate(inflater, container, false)
        initViews()
        return binding.root
    }

    private fun initViews() {
        binding.tvMessage.text = getString(if (dataType == DataType.RECENT)
            R.string.msg_delete_all_recent else R.string.msg_delete_all_short)
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnOk.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (dataType == DataType.RECENT){
                    mainViewModel.clearRecents()
                } else if (dataType == DataType.SHORT){
                    mainViewModel.clearShorts()
                }
                withContext(Dispatchers.Main){
                    Toast.makeText(requireContext(), getString(R.string.msg_clear_data_success), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
//            dismiss()
        }

        if (dataType == DataType.RECENT){
            binding.rcvData.layoutManager = LinearLayoutManager(requireContext())
            recentAdapter = RecentAdapter(false)
            binding.rcvData.adapter = recentAdapter
        } else if (dataType == DataType.SHORT){
            shortAdapter = ShortAdapter(ShortDisplayType.AVAILABLE_ONLY)
            binding.rcvData.addItemDecoration(ItemOffsetDecoration(requireContext(), R.dimen.grid_item_space))
            binding.rcvData.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.rcvData.adapter = shortAdapter
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel.recents.observe(viewLifecycleOwner){
            recentAdapter?.updateRecents(it)
        }
        mainViewModel.contacts.map {contacts ->
            recentAdapter?.updateContact(contacts)
            contacts.filter { contact -> contact.shortcut != null && contact.shortcut!! < 10 }
        }.observe(viewLifecycleOwner){ shorts ->
            val size = shorts.size
            val layoutManager = binding.rcvData.layoutManager
            (layoutManager as? GridLayoutManager)?.let {
                layoutManager.spanCount = if (size in 1..2) shorts.size else 3
            }
            shortAdapter?.updateList(shorts.sortedBy { it.shortcut })
        }
    }
    companion object {
        @JvmStatic
        fun newInstance(dataType: DataType) =
            ClearDataFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CLEAR_DATA_TYPE, dataType)
                }
            }
    }
}

class ItemOffsetDecoration(private val mItemOffset: Int) : RecyclerView.ItemDecoration() {
    constructor(
        context: Context,
        @DimenRes itemOffsetId: Int
    ) : this(context.resources.getDimensionPixelSize(itemOffsetId))

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect[mItemOffset, mItemOffset, mItemOffset] = mItemOffset
    }
}