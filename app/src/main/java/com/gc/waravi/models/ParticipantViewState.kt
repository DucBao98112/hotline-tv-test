package com.gc.waravi.models

import android.util.Log
import com.google.gson.Gson
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.room.member.RemoteRoomMember

data class ParticipantViewState(
    val id: String,
    val name: String,
    var videoTrack: RemoteVideoStream? = null,
    var isMuted: Boolean = false,
    var isMirrored: Boolean = false,
    var isDominantSpeaker: Boolean = false,
    var isLocalParticipant: Boolean = false,
    var isBlurring: Boolean = false,
    var isScreenSharing: Boolean = false
) {

    fun getRemoteVideoTrack(): RemoteVideoStream? =
        if (!isLocalParticipant) videoTrack else null

}

fun buildParticipantViewState(participant: RemoteRoomMember, id: String, name: String, isScreenViewState : Boolean = false): ParticipantViewState {
    val metadata = if(participant.metadata != null) Gson().fromJson(participant.metadata, RoomMetadata::class.java)
        else null
    return ParticipantViewState(
        id,
        name,
        null,
        isMuted = false,
        isMirrored = metadata?.mirror == true,
        isScreenSharing = isScreenViewState
    )
}