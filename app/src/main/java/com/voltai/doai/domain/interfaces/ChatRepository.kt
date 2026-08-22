package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(): Flow<List<Message>>
    suspend fun sendMessage(message: Message)
    suspend fun clearHistory()
}
