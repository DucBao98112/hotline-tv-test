package com.gc.waravi.database

import androidx.lifecycle.LiveData
import com.gc.waravi.models.CallRecent
import com.gc.waravi.models.Contact
import com.gc.waravi.models.RecentType
import java.util.Calendar
import java.util.Locale

class CallRepository(val contactDAO: ContactDAO, val recentDAO: RecentDAO) {
    val contacts : LiveData<List<Contact>> = contactDAO.getContacts()
    val recents : LiveData<List<CallRecent>> = recentDAO.getRecents()


//    fun getContact(id: String) : Contact?{
//        return contacts.find { it.contactId == id}
//    }

//    fun getShortContact(shortId: Int) : Contact?{
//        return shortContacts.find { it.shortcut == shortId}
//    }

//    fun getRecent(id: String): CallRecent?{
//        return callRecents.find { it.callerId == id}
//    }

    suspend fun updateContact(contactId: String, shortcut : Int?, name : String ) {
        val contact = Contact(name, shortcut , contactId)
        contactDAO.insert(contact)
    }

    suspend fun findContact(id: String): Contact?{
        return contactDAO.findById(id)
    }


    suspend fun deleteRecent(recent: CallRecent){
        val result = recentDAO.delete(recent.id)
//        if(result > 0){
//            callRecents.removeAll { element -> element.id == recent.id }
//        }
    }

    suspend fun deleteContact(contact: Contact){
        val result = contactDAO.delete(contact)
//        this.loadShort()
    }

    suspend fun deleteAllRecent(){
        return recentDAO.deleteAll()
    }

    suspend fun deleteAllContact(){
        contactDAO.deleteAll()
    }

    suspend fun deleteAllShort() {
        return contactDAO.clearAllShort()
//        shortContacts.forEach {
//            it.shortcut = null
//            contactDao.update(it)
//        }
//        shortContacts.clear()
    }

    suspend fun insertOrUpdateContact(contact: Contact) {
        contactDAO.insert(contact)
    }

    suspend fun insertOrUpdateRecent(callerId: String, recentType: RecentType) {
        val recent = CallRecent(
            callerId = callerId, type = recentType, time = Calendar.getInstance(
                Locale.JAPAN
            ).timeInMillis, count = 1
        )
        insertOrUpdateRecent(recent)
    }

    suspend fun insertOrUpdateRecent(recent: CallRecent) {
        val lastRecent = recentDAO.getLastRecent()
        if(lastRecent != null && lastRecent.callerId == recent.callerId && lastRecent.type == recent.type){
            recent.id = lastRecent.id
            recent.count = lastRecent.count + 1
        }
        recentDAO.save(recent)
    }

//    private suspend fun getLastRecent() : CallRecent? {
//        return recentDAO.getLastRecent()
//    }

}