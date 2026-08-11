package com.behaviorlens.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.behaviorlens.app.data.db.AppDatabase
import com.behaviorlens.app.data.models.SessionEntity
import com.behaviorlens.app.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository(AppDatabase.getInstance(application))

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions

    init { loadSessions() }

    fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = repository.getRecentSessions()
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.deleteSession(id)
            loadSessions()
        }
    }
}
