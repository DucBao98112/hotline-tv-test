package com.gc.waravi.skyway.room

import android.view.View
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.room.Room
import com.ntt.skyway.room.member.RemoteRoomMember
import com.ntt.skyway.room.member.RoomMember

sealed class RoomEvent {

    object Connecting : RoomEvent()
    data class Connected(
        val room: Room
    ) : RoomEvent()
    object Disconnected : RoomEvent()
    object ConnectFailure : RoomEvent()
    object MaxParticipantFailure : RoomEvent()

    sealed class RemoteParticipantEvent : RoomEvent() {

        data class RemoteParticipantConnected(val participant: RemoteRoomMember) : RemoteParticipantEvent()
        data class RemoteParticipantDisconnected(val participant: RemoteRoomMember) : RemoteParticipantEvent()
        data class VideoTrackUpdated(val publisher: RoomMember, val videoTrack: RemoteVideoStream?) : RemoteParticipantEvent()
        data class TrackSwitchOff(val sid: String, val videoTrack: RemoteVideoStream, val switchOff: Boolean) : RemoteParticipantEvent()
        data class ScreenTrackUpdated(
            val publisher: RoomMember,
            val screenTrack: RemoteVideoStream,
        ) : RemoteParticipantEvent()
        data class MuteRemoteParticipant(val sid: String, val mute: Boolean) : RemoteParticipantEvent()
        data class Chat(val publisher: RoomMember, val data: String) : RemoteParticipantEvent()
        data class Blur(val publisher: RoomMember, val isEnable: Boolean) : RemoteParticipantEvent()
        data class ScreenSharing(val publisher: RoomMember, val isOn: Boolean) : RemoteParticipantEvent()
    }

    sealed class LocalParticipantEvent : RoomEvent() {
        data class VideoTrackUpdated(val videoView: View?, val videoTrack: LocalVideoStream?) : LocalParticipantEvent()
        object VideoEnabled : LocalParticipantEvent()
        object VideoDisabled : LocalParticipantEvent()
        object AudioOn : LocalParticipantEvent()
        object AudioOff : LocalParticipantEvent()
        object AudioEnabled : LocalParticipantEvent()
        object AudioDisabled : LocalParticipantEvent()
    }
}
