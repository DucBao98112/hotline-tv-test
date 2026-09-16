package com.gc.waravi.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.gc.waravi.BuildConfig

import com.gc.waravi.R
import com.gc.waravi.models.CallRecent
import com.gc.waravi.models.Contact
import com.gc.waravi.models.RecentTypeConverter

@Database(entities = [CallRecent::class, Contact::class], version = 3)

@TypeConverters(RecentTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDAO
    abstract fun contactDao(): ContactDAO

    companion object {
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE =
                        Room.databaseBuilder(context.applicationContext,
                            AppDatabase::class.java, BuildConfig.DatabaseName)
                            .addMigrations(Migration(1, 2){
                                it.execSQL("ALTER TABLE recents ADD COLUMN shortcut INTEGER;")
                            })
                            .addMigrations(Migration(2, 3){
                                it.execSQL("ALTER TABLE contacts ADD COLUMN shortcut INTEGER;")
                            })
                            .build()
                }
            }
            return INSTANCE!!
        }
    }
}