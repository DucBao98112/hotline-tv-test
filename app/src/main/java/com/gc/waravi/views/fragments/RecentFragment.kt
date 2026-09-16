package com.gc.waravi.views.fragments

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentRecentsBinding
import com.gc.waravi.databinding.LayoutContactDetailBinding
import com.gc.waravi.databinding.LayoutEditContactBinding
import com.gc.waravi.models.CallRecent
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.adapters.OnItemClickListener
import com.gc.waravi.views.adapters.RecentAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class RecentFragment : BaseFragment<FragmentRecentsBinding>() {
    private lateinit var recentAdapter : RecentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.contacts.observe(viewLifecycleOwner){
            recentAdapter.updateContact(it)
        }
        viewModel.recents.observe(viewLifecycleOwner){
            recentAdapter.updateRecents(it)
            binding.tvMessage.visibility = if(it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentRecentsBinding {
        return FragmentRecentsBinding.inflate(inflater, container, false)
    }


    override fun initViews() {
        binding.rcvRecent.layoutManager = LinearLayoutManager(requireContext())
        recentAdapter = RecentAdapter()
        binding.rcvRecent.adapter = recentAdapter
        recentAdapter.setOnItemClickListener(object : OnItemClickListener{
            override fun onItemClick(item: CallRecent) {
                showDetailDialog(item)
            }

            override fun onInfoClick(item: CallRecent) {
                showEditDialog(item)
            }
        })
    }

    private fun saveContact(callerId: String, inputName: String, inputShort: String, onSuccess: () -> Unit){
        if (inputName.isNotEmpty() && callerId.isNotEmpty()) {

            val shortDial = inputShort.toIntOrNull()
            if(shortDial != null && shortDial.toString().length > 1){
                Toast.makeText(requireContext(), getString(R.string.msg_short_invalid), Toast.LENGTH_SHORT)
                    .show()
                return
            }
            val contact = if(shortDial != null) viewModel.getContactByShort(shortDial) else null
            if (contact != null && contact.contactId != callerId) {
                Toast.makeText(requireContext(), getString(R.string.msg_short_exits), Toast.LENGTH_SHORT)
                    .show()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.updateContact(callerId, shortDial, inputName)
                    recentAdapter.update(callerId, inputName, inputShort)
                    onSuccess()
                }
            }
        }
    }

    private fun deleteRecent(recent: CallRecent, onSuccess: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO){
                viewModel.deleteRecent(recent)
                onSuccess()
            }
        }
    }

    private fun deleteContact(contactId: String, onSuccess: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO){
                viewModel.deleteContact(contactId)
                val avatar = Utils.getImageFileFromLocal(requireContext(), String.format("%s.png", contactId))
                avatar?.delete()
                onSuccess()
            }
        }
    }

    private fun showDetailDialog(recent: CallRecent){
        val contact = viewModel.getContact(recent.callerId)

        val dialog = object : Dialog(requireContext(), R.style.DialogTheme){
            override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_CALL){
                    this.dismiss()
                    viewModel.makeCallById(recent.callerId)
                    return true
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        val dialogBinding = LayoutContactDetailBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setContentView(dialogBinding.root)
        dialogBinding.btnBack.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnEdit.setOnClickListener {
            showEditDialog(recent)
            dialog.dismiss()
        }
        dialogBinding.btnDelete.setOnClickListener {
            this.deleteRecent(recent){
                dialog.dismiss()
            }
        }
        dialogBinding.btnCall.setOnClickListener {
            dialog.dismiss()
            viewModel.makeCallById(recent.callerId)
        }
        dialogBinding.tvId.text = recent.callerId
        dialogBinding.tvName.text = contact?.name ?: recent.callerId
        dialogBinding.tvId.visibility = if (contact != null) View.VISIBLE else View.GONE
        val avatar = Utils.getImageFileFromLocal(requireContext(), String.format("%s.png", recent.callerId))
        if (avatar != null && avatar.exists()){
            Glide.with(this)
                .load(avatar)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .error(R.drawable.ic_logo_circle)
                .circleCrop()
                .into(dialogBinding.imvAvatar)
        } else{
            dialogBinding.imvAvatar.setImageResource(R.drawable.ic_logo_circle)
        }
        dialog.show()
    }

    private fun showEditDialog(recent: CallRecent){
        var isDelete = false
        fun showEditMode(dialogBinding: LayoutEditContactBinding){
            dialogBinding.tvTitle.text = getString(if (isDelete) R.string.msg_data_delete_confirm
                else R.string.txt_edit)
            dialogBinding.btnUpdate.text = getString(if (isDelete) R.string.txt_delete
                else R.string.txt_register)
            dialogBinding.btnDelete.visibility = if(isDelete) View.GONE else View.VISIBLE
            dialogBinding.spinnerShort.isEnabled = !isDelete
            dialogBinding.edtName.isEnabled = !isDelete
            if(isDelete){
                dialogBinding.btnCancel.requestFocus()
            }
        }

        val contact = viewModel.getContact(recent.callerId)

        val dialog = Dialog(requireContext(), R.style.DialogTheme)
        val dialogBinding = LayoutEditContactBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setContentView(dialogBinding.root)
        dialogBinding.btnCancel.setOnClickListener {
            if (isDelete){
                isDelete = false
                showEditMode(dialogBinding)
            } else{
                dialog.dismiss()
            }
        }
        dialogBinding.btnUpdate.setOnClickListener {
            if(isDelete){
                this.deleteContact(recent.callerId){
                    dialog.dismiss()
                }
            } else{
                saveContact(recent.callerId, dialogBinding.edtName.text.toString(),
                    dialogBinding.spinnerShort.selectedItem as String)
                {
                    dialog.dismiss()
                }
            }
        }
        dialogBinding.btnDelete.setOnClickListener {
            isDelete = true
            showEditMode(dialogBinding)
        }
        dialogBinding.tvId.text = recent.callerId
        dialogBinding.edtName.setText(contact?.name)
        dialogBinding.edtName.setOnEditorActionListener { edittext, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                this.closeKeyboard(edittext)
                dialogBinding.btnUpdate.requestFocus()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        val shortList = listOf("", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        val ad = ArrayAdapter(
            dialog.context,
            R.layout.view_spinner_item,
            shortList
        )
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerShort.adapter = ad
        contact?.shortcut?.let {
            dialogBinding.spinnerShort.setSelection(shortList.indexOf(it.toString()))
        }
        dialog.show()
        dialogBinding.spinnerShort.requestFocus()
    }
}