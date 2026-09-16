package com.gc.waravi.views.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gc.waravi.database.CallRepository
import com.gc.waravi.models.CallRecent
import com.gc.waravi.models.Contact
import com.gc.waravi.models.RecentType
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainViewModel(private val callRepository: CallRepository) : ViewModel() {
    private val _currentPeerId = MutableLiveData<String>()
    private val _p2pCallInput = MutableLiveData<String>()
    private val _p2pRoomInput = MutableLiveData<String>()
    private val _contacts = MutableLiveData<List<Contact>>()
    private val _recents = MutableLiveData<List<CallRecent>>()

    var contacts: LiveData<List<Contact>> = _contacts
    var recents: LiveData<List<CallRecent>> = _recents

    private val _callAction = PublishSubject.create<String>()
    val callAction = _callAction.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
    private val _roomAction = PublishSubject.create<String>()
    val roomAction = _roomAction.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    var currentPeerId : LiveData<String> = _currentPeerId
    var p2pCallInput: LiveData<String> = _p2pCallInput
    var p2pRoomInput: LiveData<String> = _p2pRoomInput
    var clearCallInput = MutableLiveData<Boolean>()

    init {
        onCreate()
    }

    fun onCreate() = viewModelScope.launch(Dispatchers.IO){
        contacts = callRepository.contacts
        recents = callRepository.recents
    }

    fun setCurrentPeerId(peerId: String){
        _currentPeerId.value = peerId
    }

    fun clearCallInput(){
        clearCallInput.value = true
    }

    fun setCallInput(number: String){
        _p2pCallInput.value = number
    }

    fun setRoomInput(number: String){
        _p2pRoomInput.value = number
    }

    suspend fun updateContact(contactId: String, shortcut : Int?, name : String ) {
        val contact = Contact(name, shortcut , contactId)
        callRepository.insertOrUpdateContact(contact)
    }

    suspend fun clearRecents(){
        callRepository.deleteAllRecent()
    }

    suspend fun clearShorts(){
        callRepository.deleteAllShort()
    }

    suspend fun saveCallRecent(peerId : String, type: RecentType){
        val callRecents = recents.value ?: return
        val recent = CallRecent(
            callerId = peerId, type = type, time = Calendar.getInstance(
                Locale.JAPAN
            ).timeInMillis, count = 1
        )
        val lastRecent = if(callRecents.isNotEmpty()) callRecents[0] else null
        if(lastRecent != null && lastRecent.callerId == recent.callerId && lastRecent.type == recent.type){
            recent.id = lastRecent.id
            recent.count = lastRecent.count + 1
        }
        callRepository.insertOrUpdateRecent(recent)
    }

    suspend fun deleteRecent(recent: CallRecent){
        callRepository.deleteRecent(recent)
    }

    suspend fun deleteContact(contactId: String){
        getContact(contactId)?.let {
            callRepository.deleteContact(it)
        }
    }

    fun getContact(callerId: String): Contact? {
        return contacts.value?.find { it.contactId == callerId}
    }

    fun makeCallByCurrentId(){
        _callAction.onNext(p2pCallInput.value)
    }

    fun makeCallById(id: String){
        _callAction.onNext(id)
    }

    fun joinRoomByCurrentId(){
        _roomAction.onNext(p2pRoomInput.value)
    }

    fun getContactByShort(shortcut: Int): Contact?{
        return contacts.value?.find { it.shortcut == shortcut }
    }

}

class MainViewModelFactory(private val repository: CallRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}