package com.gc.waravi.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.utils.Utils
import com.gc.waravi.databinding.ViewChatMeBinding
import com.gc.waravi.databinding.ViewChatOtherBinding
import com.gc.waravi.databinding.ViewChatOverlayItemBinding
import com.gc.waravi.models.ChatMessage

/**
 * チャットリストのアダプタ
 */
class GroupChatAdapter(private val isOverlay: Boolean = false) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object{
        const val VIEW_TYPE_ME = 0
        const val VIEW_TYPE_OTHER = 1
        const val VIEW_TYPE_OVERLAY = 2
    }

    private val messages = arrayListOf<ChatMessage>()

    override fun getItemViewType(position: Int): Int {
        if (isOverlay) return VIEW_TYPE_OVERLAY
        val message  = getItem(position)
        return if (message.isMine) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ME -> ChatMeViewHolder(ViewChatMeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_OTHER -> ChatOtherViewHolder(ViewChatOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> ChatOverlayViewHolder(ViewChatOverlayItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is ChatMeViewHolder -> holder.bindMessage(message)
            is ChatOtherViewHolder -> {
                val isSame = position < itemCount - 1 && getItem(position + 1).sender == message.sender
                holder.bindMessage(message, isSame)
            }
            is ChatOverlayViewHolder -> holder.bindMessage(message)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    private fun getItem(position: Int) : ChatMessage{
        return messages[position]
    }

    fun addMessage(message: ChatMessage){
        messages.add(0, message)
        notifyItemInserted(0)
    }

    fun addMessages(message: List<ChatMessage>){
        this.messages.addAll(message)
        notifyDataSetChanged()
    }

    fun clear(){
        this.messages.clear()
        notifyDataSetChanged()
    }

    fun getAllChatMessage(): List<ChatMessage> {
        return messages
    }
}

class ChatMeViewHolder(val binding: ViewChatMeBinding) : RecyclerView.ViewHolder(binding.root){
    fun bindMessage(message: ChatMessage) {
        binding.tvMessage.text = message.message
        binding.tvTime.text = Utils.getFormattedTime(message.timeStamp, "HH:mm")
    }

}

class ChatOtherViewHolder(val binding: ViewChatOtherBinding) : RecyclerView.ViewHolder(binding.root){
    fun bindMessage(message: ChatMessage, isSamePeerId: Boolean) {
        binding.tvMessage.text = message.message
        binding.tvName.text = message.sender
        binding.tvTime.text = Utils.getFormattedTime(message.timeStamp, "HH:mm")
        binding.tvName.visibility = if (isSamePeerId) View.GONE else View.VISIBLE
    }

}

class ChatOverlayViewHolder(val binding: ViewChatOverlayItemBinding) : RecyclerView.ViewHolder(binding.root){
    fun bindMessage(message: ChatMessage) {
        binding.tvMessage.text = message.message
        binding.tvName.text = message.sender
        binding.tvTime.text = Utils.getFormattedTime(message.timeStamp, "HH:mm:ss")
    }
}