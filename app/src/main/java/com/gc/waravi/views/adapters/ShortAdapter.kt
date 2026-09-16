package com.gc.waravi.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.gc.waravi.R
import com.gc.waravi.databinding.ViewShortBinding
import com.gc.waravi.databinding.ViewShortNotRegisteredBinding
import com.gc.waravi.models.Contact
import com.gc.waravi.utils.Utils

enum class ShortDisplayType{
    AVAILABLE_ONLY,
    INCLUDE_UNREGISTERED
}

class ShortAdapter(private val type: ShortDisplayType = ShortDisplayType.INCLUDE_UNREGISTERED) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var itemClick: OnShortItemClickListener? = null
    private var shortList: ArrayList<Contact> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 2) ShortRegisterViewHolder(
            ViewShortNotRegisteredBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        ) else ShortViewHolder(
            ViewShortBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    fun updateList(contacts: List<Contact>){
        shortList.clear()
        shortList.addAll(contacts)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val index = position + 1
        if (holder is ShortViewHolder){
            val contact = if(type == ShortDisplayType.INCLUDE_UNREGISTERED)
                shortList.find { it.shortcut == index } else shortList[position]
            contact?.let {
                holder.itemView.setOnClickListener {
                    itemClick?.onItemClick(contact)
                }
                holder.itemView.setOnLongClickListener {
                    itemClick?.onItemLongClick(contact)
                    return@setOnLongClickListener false
                }
                holder.bind(contact.shortcut ?: index, it)
            }
        } else if(holder is ShortRegisterViewHolder){
            holder.itemView.setOnClickListener {
                itemClick?.onNewShortClick(index)
            }
            holder.bind(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (type == ShortDisplayType.AVAILABLE_ONLY){
            return 1
        }
        val contact = shortList.find { contact -> contact.shortcut == (position + 1) }
        return if (contact != null) 1 else 2
    }

    fun update(contactId: String, name: String, short: String) {
        for (i in shortList.indices) {
            if (shortList[i].contactId == contactId) {
                notifyItemChanged(i)
            }
        }
    }

    override fun getItemCount(): Int {
        return if (type == ShortDisplayType.INCLUDE_UNREGISTERED) 9 else shortList.size
    }

    fun setOnItemClickListener(itemClick: OnShortItemClickListener) {
        this.itemClick = itemClick
    }

    fun removeContact(shortcut: Int) {
        val iterator = shortList.iterator()
        while (iterator.hasNext()) {
            val contact = iterator.next()
            if (contact.shortcut == shortcut) {
                iterator.remove()
            }
        }
        notifyDataSetChanged()
    }

    fun removeAll() {
        shortList.clear()
        notifyDataSetChanged()
    }

    class ShortViewHolder(val binding: ViewShortBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(index: Int, contact: Contact?) {
            val name = contact?.name

            binding.tvShort.text = String.format("%d", index)
            binding.tvName.text = name ?: ""
            binding.tvId.text = contact?.contactId ?: ""
            val avatar = Utils.getImageFileFromLocal(binding.root.context, String.format("%s.png", contact?.contactId ?: "unknown"))
            if (avatar != null && avatar.exists()){
                Glide.with(binding.root.context)
                    .load(avatar)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .error(R.drawable.ic_logo_circle)
                    .circleCrop()
                    .into(binding.imvAvatar)
            } else{
                binding.imvAvatar.setImageResource(R.drawable.ic_logo_circle)
            }
        }
    }

    class ShortRegisterViewHolder(val binding: ViewShortNotRegisteredBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(index: Int) {
            binding.tvShort.text = String.format("%d", index)
        }

    }
}

interface OnShortItemClickListener {
    fun onItemClick(item: Contact)
    fun onItemLongClick(item: Contact)
    fun onNewShortClick(short: Int)
}