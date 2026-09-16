package com.gc.waravi.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.gc.waravi.models.Contact

@Dao
interface ContactDAO {
    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<Contact>

    @Query("SELECT * FROM contacts")
    fun getContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM contacts WHERE shortcut < 11 ORDER BY shortcut ASC")
    suspend fun getShort(): List<Contact>

    @Query("UPDATE contacts SET shortcut = null")
    suspend fun clearAllShort()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(contact: Contact)

    @Query("SELECT * from contacts WHERE contactId = :id")
    suspend fun findById(id: String) : Contact?
}