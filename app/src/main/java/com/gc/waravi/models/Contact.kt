package com.gc.waravi.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(var name : String, var shortcut : Int?, @PrimaryKey val contactId: String)