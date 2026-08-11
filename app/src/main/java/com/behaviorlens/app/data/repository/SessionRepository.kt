package com.behaviorlens.app.data.repository

import com.behaviorlens.app.data.db.AppDatabase
import com.behaviorlens.app.data.models.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(private val db: AppDatabase) {
    suspend fun saveSession(session: SessionEntity): Long =
        withContext(Dispatchers.IO) { db.sessionDao().insert(session) }

    suspend fun getRecentSessions(): List<SessionEntity> =
        withContext(Dispatchers.IO) { db.sessionDao().getRecentSessions() }

    suspend fun deleteSession(id: Long) =
        withContext(Dispatchers.IO) { db.sessionDao().delete(id) }
}
