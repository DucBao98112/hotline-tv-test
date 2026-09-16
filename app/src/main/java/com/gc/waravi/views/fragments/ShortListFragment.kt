package com.gc.waravi.views.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentShortListBinding
import com.gc.waravi.databinding.LayoutContactDetailBinding
import com.gc.waravi.databinding.LayoutEditContactBinding
import com.gc.waravi.databinding.LayoutNewShortBinding
import com.gc.waravi.models.Contact
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.adapters.OnShortItemClickListener
import com.gc.waravi.views.adapters.ShortAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ShortListFragment : BaseFragment<FragmentShortListBinding>(){
    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentShortListBinding {
        return FragmentShortListBinding.inflate(inflater, container, false)
    }

    private lateinit var shortAdapter : ShortAdapter

    override fun initViews() {
        binding.rcvShortList.layoutManager = GridLayoutManager(requireContext(), 3)
        shortAdapter = ShortAdapter()
        binding.rcvShortList.adapter = shortAdapter

        shortAdapter.setOnItemClickListener(object : OnShortItemClickListener{
            override fun onItemClick(item: Contact) {
                showDetailDialog(item)
            }

            override fun onItemLongClick(item: Contact) {
                showEditDialog(item)
            }

            override fun onNewShortClick(short: Int) {
                showAddingDialog(short)
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.contacts.map {
            it.filter { contact -> contact.shortcut != null && contact.shortcut!! < 10 }
        }.observe(viewLifecycleOwner){
            shortAdapter.updateList(it)
        }
    }

    private fun saveContact(callerId: String, inputName: String, inputShort: String, onSuccess: () -> Unit){
        if (inputName.isNotEmpty() && callerId.isNotEmpty()) {

            val shortDial = inputShort.toIntOrNull()
            if(shortDial != null && shortDial.toString().length > 1){
                Toast.makeText(requireContext(), getString(R.string.msg_short_invalid), Toast.LENGTH_SHORT)
                    .show()
                return
            }
            val contact = viewModel.getContactByShort(shortDial!!)
            if (contact != null && contact.contactId != callerId) {
                Toast.makeText(requireContext(), getString(R.string.msg_short_exits), Toast.LENGTH_SHORT)
                    .show()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.updateContact(callerId, shortDial, inputName)
                    onSuccess()
                }
            }
        }
    }

    private fun deleteShort(contact: Contact, onSuccess: () -> Unit){
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateContact(contact.contactId, null, contact.name)
            onSuccess()
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

    private fun showDetailDialog(contact: Contact){
        val dialog = object : Dialog(requireContext(), R.style.DialogTheme){
            override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_CALL){
                    this.dismiss()
                    viewModel.makeCallById(contact.contactId)
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
            showEditDialog(contact)
            dialog.dismiss()
        }
        dialogBinding.btnDelete.setOnClickListener {
            this.deleteShort(contact){
                dialog.dismiss()
            }
        }
        dialogBinding.btnCall.setOnClickListener {
            dialog.dismiss()
            viewModel.makeCallById(contact.contactId)
        }
        dialogBinding.tvId.text = contact.contactId
        dialogBinding.tvName.text = contact.name.ifEmpty { contact.contactId }
        dialogBinding.tvId.visibility = if (contact.name.isNotEmpty()) View.VISIBLE else View.GONE
        val avatar = Utils.getImageFileFromLocal(requireContext(), String.format("%s.png", contact.contactId))
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

    private fun showEditDialog(contact: Contact){
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
                this.deleteContact(contact.contactId){
                    dialog.dismiss()
                }
            } else{
                saveContact(contact.contactId, dialogBinding.edtName.text.toString(),
                    dialogBinding.spinnerShort.selectedItem as String){
                    dialog.dismiss()
                }
            }
        }
        dialogBinding.btnDelete.setOnClickListener {
            isDelete = true
            showEditMode(dialogBinding)
        }
        dialogBinding.tvId.text = contact.contactId
        dialogBinding.edtName.setText(contact.name)
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
        contact.shortcut?.let {
            dialogBinding.spinnerShort.setSelection(shortList.indexOf(it.toString()))
        }
        dialog.show()
        dialogBinding.spinnerShort.requestFocus()
    }

    private fun showAddingDialog(shortId: Int){
        val dialog = Dialog(requireContext(), R.style.DialogTheme)
        val dialogBinding = LayoutNewShortBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setContentView(dialogBinding.root)
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnUpdate.setOnClickListener {
            val id = dialogBinding.tvId.text.toString()
            val name = dialogBinding.edtName.text.toString()
            saveContact(id, name, shortId.toString()){
                val contact = viewModel.getContact(id)
                if (contact != null){
                    showDetailDialog(contact)
                }
                dialog.dismiss()

            }
        }
        dialogBinding.tvShort.text = String.format("%d", shortId)
        dialogBinding.tvId.setOnEditorActionListener { edittext, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                this.closeKeyboard(edittext)
                dialogBinding.btnUpdate.requestFocus()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        dialog.show()
    }
}