package com.gc.waravi.models

import androidx.room.TypeConverter

enum class RecentType {
    Outgoing, Incoming, Missed
}

class RecentTypeConverter{
    @TypeConverter
    fun fromType(type : RecentType) : Int{
        return RecentType.values().indexOf(type)
    }
    @TypeConverter
    fun toType(typeInt : Int) : RecentType{
        return RecentType.values()[typeInt]
    }
}