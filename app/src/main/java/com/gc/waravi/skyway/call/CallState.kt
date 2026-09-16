package com.gc.waravi.skyway.call

enum class CallState(val value: String) {
    WAITING("waiting"),
    CALLING("calling"),
    IN_CALL("in_call"),
    END("end"),
    IDLE("idle")
}