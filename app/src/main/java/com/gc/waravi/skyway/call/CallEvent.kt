package com.gc.waravi.skyway.call

import android.view.View
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.room.member.LocalRoomMember
import com.ntt.skyway.room.member.RemoteRoomMember
import com.ntt.skyway.room.member.RoomMember

sealed class CallEvent {

    data class Error(val exception: Exception? = null) : CallEvent()
    data class Connected(val self: LocalRoomMember) : CallEvent()
    data class IncomingCall(val callerId: String) : CallEvent()
//    data class VideoTrackPublished(val videoTrack: RemoteVideoStream) : CallEvent()
//    data class VideoTrackUnPublished(val subId: String) : CallEvent()
    data class VideoTrackSwitched(val isEnable: Boolean) : CallEvent()
    data class Chat(val publisher: RoomMember, val data: String) : CallEvent()
    data class Blur(val publisher: RoomMember, val isEnable: Boolean) : CallEvent()
    data class ScreenSharing(val publisher: RoomMember, val isOn: Boolean) : CallEvent()
    data class CallStarted(val participant: RemoteRoomMember) : CallEvent()
    object CallEnded : CallEvent()
    object CallCanceled : CallEvent()
    object CallRejected : CallEvent()
    object CalleeBusy : CallEvent()

    sealed class RemoteCallParticipantEvent : CallEvent() {
        data class VideoTrackUpdated(val publisher: RoomMember, val videoTrack: RemoteVideoStream?) : CallEvent.RemoteCallParticipantEvent()
        data class TrackSwitchOff(val sid: String, val videoTrack: RemoteVideoStream, val switchOff: Boolean) : RemoteCallParticipantEvent()
        data class ScreenTrackUpdated(
            val sid: String,
            val screenTrack: RemoteVideoStream?,
        ) : RemoteCallParticipantEvent()
        data class MuteRemoteParticipant(val sid: String, val mute: Boolean) : RemoteCallParticipantEvent()
    }

    sealed class LocalParticipantEvent : CallEvent() {
        data class VideoTrackUpdated(val videoView: View?, val videoTrack: LocalVideoStream?) : LocalParticipantEvent()
        object VideoEnabled : LocalParticipantEvent()
        object VideoDisabled : LocalParticipantEvent()
        object AudioOn : LocalParticipantEvent()
        object AudioOff : LocalParticipantEvent()
        object AudioEnabled : LocalParticipantEvent()
        object AudioDisabled : LocalParticipantEvent()
    }
}
