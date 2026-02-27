package com.example.mykmp.domain.repository

import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ConversationSummary

/**
 * Репозиторий для персистентного хранения истории чата и настроек.
 */
interface ChatHistoryRepository {
    fun saveHistory(messages: List<ChatMessage>)
    fun loadHistory(): List<ChatMessage>
    fun clearHistory()

    fun saveSettings(config: ChatRequestConfig)
    fun loadSettings(): ChatRequestConfig?

    fun saveSummary(summary: ConversationSummary)
    fun loadSummary(): ConversationSummary?
    fun clearSummary()
}
