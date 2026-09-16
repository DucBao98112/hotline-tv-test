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
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class InCallViewModel(private val callRepository: CallRepository) : ViewModel() {
    private val _contact = MutableLiveData<Contact>()
    var contact : LiveData<Contact> = _contact

    fun getContact(callerId: String){
        viewModelScope.launch {
            val contact = callRepository.findContact(callerId)
            contact?.let {
                _contact.value = it
            }
        }

    }

    suspend fun saveCallRecent(peerId : String, type: RecentType){
        val recent = CallRecent(
            callerId = peerId, type = type, time = Calendar.getInstance(
                Locale.JAPAN
            ).timeInMillis, count = 1
        )
        callRepository.insertOrUpdateRecent(recent)
    }
}

class InCallViewModelFactory(private val repository: CallRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InCallViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InCallViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}