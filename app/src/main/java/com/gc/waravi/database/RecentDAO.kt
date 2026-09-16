package com.gc.waravi.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.gc.waravi.models.CallRecent

@Dao
interface RecentDAO {
    @Query("SELECT * FROM recents ORDER BY time DESC")
    suspend fun getAll(): List<CallRecent>

    @Query("SELECT * FROM recents ORDER BY time DESC")
    fun getRecents(): LiveData<List<CallRecent>>

    @Query("SELECT * FROM recents ORDER BY time DESC LIMIT 1")
    suspend fun getLastRecent(): CallRecent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(recent: CallRecent) : Long

    @Query("DELETE FROM recents WHERE id = :recentId")
    suspend fun delete(recentId: Int) : Int

    @Query("DELETE FROM recents")
    suspend fun deleteAll()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(recent: CallRecent)
}