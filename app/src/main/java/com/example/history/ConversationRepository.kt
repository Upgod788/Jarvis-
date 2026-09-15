package com.example.history

import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val conversationDao: ConversationDao) {
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun getRecent(limit: Int = 10): Flow<List<ConversationEntity>> =
        conversationDao.getRecentConversations(limit)

    fun search(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    suspend fun saveConversation(
        command: String,
        response: String,
        tool: String? = null,
        result: String? = null,
        success: Boolean = true
    ): Long {
        val entity = ConversationEntity(
            userCommand = command,
            assistantResponse = response,
            toolUsed = tool,
            executionResult = result,
            isSuccess = success
        )
        return conversationDao.insertConversation(entity)
    }

    suspend fun deleteById(id: Long): Int =
        conversationDao.deleteConversationById(id)

    suspend fun clearAll(): Int =
        conversationDao.clearAllConversations()
}
