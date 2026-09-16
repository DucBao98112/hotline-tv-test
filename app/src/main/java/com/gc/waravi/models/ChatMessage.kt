package com.gc.waravi.models

/**
 * チャットオブジェクト
 */
data class ChatMessage(val message: String, val timeStamp: Long,
                       val sender: String, val type : Int, var isMine : Boolean) {
}