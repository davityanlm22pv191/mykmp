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

    // Sticky Facts
    fun saveStickyFacts(factsJson: String)
    fun loadStickyFacts(): String?
    fun clearStickyFacts()

    // Branching
    fun saveBranchingState(stateJson: String)
    fun loadBranchingState(): String?
    fun clearBranchingState()
    fun saveBranchMessages(branchId: String, messagesJson: String)
    fun loadBranchMessages(branchId: String): String?
    fun clearBranchMessages(branchId: String)
}
