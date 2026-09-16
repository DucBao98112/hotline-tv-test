package com.gc.waravi.views.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.R
import com.gc.waravi.databinding.ViewRecentBinding
import com.gc.waravi.models.CallRecent
import com.gc.waravi.models.Contact
import com.gc.waravi.models.RecentType
import com.google.android.material.color.MaterialColors

class RecentAdapter(private val isEditable: Boolean = true) : RecyclerView.Adapter<RecentViewHolder>() {
    private val recentList = arrayListOf<CallRecent>()
    private val contactList = arrayListOf<Contact>()
    var itemClick : OnItemClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val holder = RecentViewHolder(
            ViewRecentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
        holder.binding.btnInfo.visibility = if (isEditable) View.VISIBLE else View.GONE
        return holder
    }

    fun updateRecents(recents: List<CallRecent>){
        this.recentList.clear()
        this.recentList.addAll(recents)
        notifyDataSetChanged()
    }

    fun updateContact(contacts: List<Contact>){
        contactList.clear()
        contactList.addAll(contacts)
        if (itemCount != 0){
            notifyDataSetChanged()
        }
    }

    fun update(contactId: String, name: String, short : String){
        for (i in recentList.indices){
            if (recentList[i].callerId == contactId){
                notifyItemChanged(i)
            }
        }
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val recent = recentList[position]
        holder.binding.btnInfo.setOnClickListener {
            itemClick?.onInfoClick(recent)
        }
        holder.binding.layoutInfo.setOnClickListener {
            itemClick?.onItemClick(recent)
        }
        val contact = this.getContact(recent.callerId)
        holder.bind(recent, contact)
    }

    fun getContact(contactId: String) : Contact?{
        return contactList.find { it.contactId == contactId}
    }

    override fun getItemCount(): Int {
        return recentList.size
    }

    fun setOnItemClickListener(itemClick: OnItemClickListener){
        this.itemClick = itemClick
    }

    fun removeRecent(time: Long) {
        for (i in recentList.indices){
            if (recentList[i].time == time){
                recentList.removeAt(i)
            }
        }
        notifyDataSetChanged()
    }

    fun removeAll(){
        recentList.clear()
        notifyDataSetChanged()
    }

}

class RecentViewHolder(val binding: ViewRecentBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(callRecent: CallRecent, contact : Contact?) {
        val name = contact?.name ?: ""
//        val short = contact?.shortcut.toString()
        binding.tvId.text = callRecent.callerId
        binding.tvName.text = if(callRecent.count > 1) String.format("%s (%d)", name, callRecent.count) else name
        binding.tvTime.text = callRecent.getTimeString()

        val outgoingTextColor = MaterialColors.getColor(binding.root.context, R.attr.text_color_light, Color.BLACK)
        val incomingTextColor = ContextCompat.getColor(
            binding.root.context,
            R.color.red
        )
        val missedTextColor = ContextCompat.getColor(
            binding.root.context,
            R.color.yellow
        )
        val color = when(callRecent.type){
            RecentType.Outgoing -> outgoingTextColor
            RecentType.Incoming -> incomingTextColor
            RecentType.Missed -> missedTextColor
        }
        binding.tvId.setTextColor(color)
        binding.tvName.setTextColor(color)
    }

}

interface OnItemClickListener {
    fun onItemClick(item: CallRecent)
    fun onInfoClick(item: CallRecent)
}