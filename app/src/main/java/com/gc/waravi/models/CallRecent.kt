package com.gc.waravi.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gc.waravi.utils.Utils

@Entity(tableName = "recents")
data class CallRecent(
    @PrimaryKey(autoGenerate = true)
    var id : Int = 0,
    val callerId: String,
    val type : RecentType,
    var time : Long,
    var count: Int,
    var shortcut: Int? = null ) {
    fun getTimeString(): String {
        return Utils.getFormattedRecentTime(time)
    }
    fun updateShortcut(newShortcut : Int) {
        shortcut = newShortcut
    }
}
