package com.gc.waravi

import com.google.gson.annotations.SerializedName

/**
 * コールのメッセージコード
 */
enum class MessageCode(val code: String) {
    CallCanceled("CALL_CANCELED"), CONNECTED("CONNECTED"), CallDeclined("CALL_DECLINED")
    , CallEnded("CALL_ENDED"), IncomingCallBusy("CALL_BUSY"), CallWithoutStream("CALL_WITHOUT_STREAM"),
    LeaveRoom("LEAVE_ROOM"), Unknown("Unknown"), Chat("CHAT"), HostId("HOST_ID"),
    BLUR("BLUR_VIDEO"), ScreenSharing("SCREEN_SHARING");
}

/**
 * コールのメッセージデータ
 */
class CallData internal constructor(
    @field:SerializedName("name") var name: String, @field:SerializedName(
        "message"
    ) var message: String
) {

    val code: MessageCode
        get() {
            for (code in MessageCode.values()) {
                if (code.code.equals(name, ignoreCase = true)) {
                    return code
                }
            }
            return MessageCode.Unknown
        }
}
