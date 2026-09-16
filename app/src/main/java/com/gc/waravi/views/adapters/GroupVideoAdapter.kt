package com.gc.waravi.views.adapters

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.databinding.LayoutGroupVideoBinding
import com.gc.waravi.models.ParticipantViewState
import com.google.android.flexbox.FlexboxLayoutManager
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 *　デフォルトのルームビデオリストのアダプタ
 */
class GroupVideoAdapter(private var screenSize: Pair<Float, Float>) : RecyclerView.Adapter<GroupVideoViewHolder>() {
    private var frameSize : Pair<Int, Int> = Pair(400, 300)
    private var participants = ArrayList<ParticipantViewState>()
    private var itemClickListener : OnVideoItemClickListener? = null
//    private var waitingForBlurringIds = HashSet<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupVideoViewHolder {
        val binding = LayoutGroupVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupVideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupVideoViewHolder, position: Int) {
//        if(itemCount == 1 && position == 0){
//            frameSize = calculateVideoFrameSize(1)
//        }
        val item = getParticipantAtPosition(position) ?: return
        holder.itemView.setOnClickListener {
            itemClickListener?.onClick(item)
        }
        holder.bind(item, frameSize)
    }

    override fun getItemCount(): Int {
        return participants.size
    }

    override fun getItemId(position: Int): Long {
        val item = getParticipantAtPosition(position)
        return item?.id?.toLongOrNull() ?: position.toLong()
    }

    private fun getParticipantAtPosition(position: Int): ParticipantViewState?{
        return if(participants.isNotEmpty()) participants[position] else null
    }

    fun getStreamById(participantId: String): RemoteVideoStream?{
        return participants.firstOrNull { it.id == participantId }?.videoTrack
    }

    fun enableBlurring(identify: String, isEnable: Boolean){
        val index = participants.indexOfFirst { it.name == identify }
        if(index != -1){
            participants[index].isBlurring = isEnable
            notifyItemChanged(index)
        }
    }

    fun addParticipant(participant: ParticipantViewState) {
        val index = participants.indexOfFirst { stream -> stream.id == participant.id }
        if(index == -1){
            participants.add(participant)
        } else{
            participants[index] = participant
        }
        refreshVideoFrame()
    }

    fun removeParticipant(sid: String){
        this.enableBlurring(sid, false)
        participants.firstOrNull { it.id == sid }?.videoTrack?.let {
            it.removeAllRenderer()
        }
        participants.removeAll { it.id == sid }
        this.refreshVideoFrame()
    }

    fun getParticipantPosition(sid: String) : Int{
        return participants.indexOfFirst { participant ->  sid == participant.id}
    }

    fun getParticipantViewState(participantId: String) : ParticipantViewState?{
        return participants.find { participantId == it.id }
    }

    private fun refreshVideoFrame(){
        frameSize = calculateVideoFrameSize(itemCount)
        notifyDataSetChanged()
    }

    fun getAllParticipants() : ArrayList<ParticipantViewState>{
        return participants
    }

    fun setOnItemClickListener(listener: OnVideoItemClickListener){
        this.itemClickListener = listener
    }

    private fun calculateVideoFrameSize(count : Int) : Pair<Int, Int>{
        val screenWidth = screenSize.first
        val screenHeight = screenSize.second
        val width : Int
        var height : Int
        val countPerColumn = ceil(sqrt(count.toFloat()))
        val row = ceil(count/countPerColumn)
        height = (screenHeight / row).toInt()
        val sWidth = if(row == 1f) screenWidth else countPerColumn * (height * 16f/9f)
        if(sWidth >= screenWidth){
            width = (screenWidth/countPerColumn).toInt()
        } else{
            width = (sWidth / countPerColumn).toInt()
        }
        height = (width * 9f/16f).toInt()
        return Pair(width, height)
    }

    fun updateVideoTrack(participantId: String, videoTrack: RemoteVideoStream) {
        val index = getParticipantPosition(participantId)
        if (index != -1){
            val participant = participants[index]
            participant.videoTrack = videoTrack
            participants[index] = participant
            notifyItemChanged(index)
        }
    }

    fun updateVideoState(sid: String, isOff: Boolean){
        val index = getParticipantPosition(sid)
        if (index != -1){
            val participant = participants[index]
            participant.isMuted = isOff
            participants[index] = participant
            notifyItemChanged(index)
        }
    }

    fun releaseVideoTracks() {
        participants.onEach {
            it.videoTrack?.removeAllRenderer()
        }
    }

}

interface OnVideoItemClickListener {
    fun onClick(participantViewState: ParticipantViewState)
}

class GroupVideoViewHolder(val binding: LayoutGroupVideoBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(participantViewState: ParticipantViewState, frameSize: Pair<Int, Int>){
        binding.viewFrame.setup()
        binding.viewFrame.setMirror(participantViewState.isMirrored)
        binding.viewFrame.setScalingType(SurfaceViewRenderer.ScalingType.SCALE_ASPECT_FIT)
        val isVideoOff = false
        val layoutParams = binding.root.layoutParams as FlexboxLayoutManager.LayoutParams
        layoutParams.width = frameSize.first
        layoutParams.height = frameSize.second
        binding.root.layoutParams = layoutParams
        binding.root.isFocusable = true
        if (participantViewState.isBlurring) {
            binding.viewFrame.alpha = 0.6f
            binding.viewMask.visibility = View.VISIBLE
        } else {
            binding.viewFrame.alpha = 1.0f
            binding.viewMask.visibility = View.GONE
        }
        binding.tvId.text = participantViewState.name
        binding.viewFrame.tag = participantViewState.id
        binding.participantTrackSwitchOffIcon.visibility = if (isVideoOff) View.VISIBLE else View.GONE
        binding.viewFrame.visibility = if (isVideoOff) View.GONE else View.VISIBLE
        participantViewState.videoTrack?.addRenderer(binding.viewFrame)
    }
}