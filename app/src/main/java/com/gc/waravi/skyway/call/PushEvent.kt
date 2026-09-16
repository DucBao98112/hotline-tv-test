package com.gc.waravi.skyway.call

sealed class PushEvent {
    object VoIP : PushEvent()
    object Cancel : PushEvent()
    object Decline : PushEvent()
    object End : PushEvent()
    object BUSY : PushEvent()

    object PushNotification {
        const val TYPE_VOIP = "voip_push"
        const val TYPE_CANCEL = "cancel"
        const val TYPE_DECLINE = "decline"
        const val TYPE_BUSY = "busy"
        const val TYPE_END = "end"
    }

    fun getPushType(): String{
        return when(this){
            is VoIP -> PushNotification.TYPE_VOIP
            is Cancel -> PushNotification.TYPE_CANCEL
            is BUSY -> PushNotification.TYPE_BUSY
            is Decline -> PushNotification.TYPE_DECLINE
            is End -> PushNotification.TYPE_END
        }
    }
}
