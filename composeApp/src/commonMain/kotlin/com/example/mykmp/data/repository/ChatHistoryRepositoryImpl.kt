package com.example.mykmp.data.repository

import com.example.mykmp.data.storage.loadFromStorage
import com.example.mykmp.data.storage.saveToStorage
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.ConversationSummary
import com.example.mykmp.domain.model.SavedSettings
import com.example.mykmp.domain.model.toConfig
import com.example.mykmp.domain.model.toSavedSettings
import com.example.mykmp.domain.repository.ChatHistoryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val KEY_HISTORY = "chat_history"
private const val KEY_SETTINGS = "chat_settings"
private const val KEY_SUMMARY = "conversation_summary"
private const val KEY_STICKY_FACTS = "sticky_facts"
private const val KEY_BRANCHING_STATE = "branching_state"
private const val KEY_BRANCH_MESSAGES_PREFIX = "branch_"

/**
 * Реализация ChatHistoryRepository через платформо-зависимое хранилище.
 * Сериализует данные в JSON и обратно.
 */
class ChatHistoryRepositoryImpl : ChatHistoryRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun saveHistory(messages: List<ChatMessage>) {
        try {
            saveToStorage(KEY_HISTORY, json.encodeToString(messages))
        } catch (e: Exception) {
            println("Failed to save chat history: ${e.message}")
        }
    }

    override fun loadHistory(): List<ChatMessage> {
        return try {
            val jsonString = loadFromStorage(KEY_HISTORY) ?: return emptyList()
            json.decodeFromString<List<ChatMessage>>(jsonString)
        } catch (e: Exception) {
            println("Failed to load chat history: ${e.message}")
            emptyList()
        }
    }

    override fun clearHistory() {
        try {
            saveToStorage(KEY_HISTORY, "[]")
            clearSummary()
        } catch (e: Exception) {
            println("Failed to clear chat history: ${e.message}")
        }
    }

    override fun saveSettings(config: ChatRequestConfig) {
        try {
            saveToStorage(KEY_SETTINGS, json.encodeToString(config.toSavedSettings()))
        } catch (e: Exception) {
            println("Failed to save settings: ${e.message}")
        }
    }

    override fun loadSettings(): ChatRequestConfig? {
        return try {
            val jsonString = loadFromStorage(KEY_SETTINGS) ?: return null
            json.decodeFromString<SavedSettings>(jsonString).toConfig()
        } catch (e: Exception) {
            println("Failed to load settings: ${e.message}")
            null
        }
    }

    override fun saveSummary(summary: ConversationSummary) {
        try {
            saveToStorage(KEY_SUMMARY, json.encodeToString(summary))
        } catch (e: Exception) {
            println("Failed to save conversation summary: ${e.message}")
        }
    }

    override fun loadSummary(): ConversationSummary? {
        return try {
            val jsonString = loadFromStorage(KEY_SUMMARY)
            if (jsonString.isNullOrBlank()) return null
            json.decodeFromString<ConversationSummary>(jsonString)
        } catch (e: Exception) {
            println("Failed to load conversation summary: ${e.message}")
            null
        }
    }

    override fun clearSummary() {
        try {
            saveToStorage(KEY_SUMMARY, "")
        } catch (e: Exception) {
            println("Failed to clear conversation summary: ${e.message}")
        }
    }

    // === Sticky Facts ===

    override fun saveStickyFacts(factsJson: String) {
        try {
            saveToStorage(KEY_STICKY_FACTS, factsJson)
        } catch (e: Exception) {
            println("Failed to save sticky facts: ${e.message}")
        }
    }

    override fun loadStickyFacts(): String? {
        return try {
            val value = loadFromStorage(KEY_STICKY_FACTS)
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            println("Failed to load sticky facts: ${e.message}")
            null
        }
    }

    override fun clearStickyFacts() {
        try {
            saveToStorage(KEY_STICKY_FACTS, "")
        } catch (e: Exception) {
            println("Failed to clear sticky facts: ${e.message}")
        }
    }

    // === Branching ===

    override fun saveBranchingState(stateJson: String) {
        try {
            saveToStorage(KEY_BRANCHING_STATE, stateJson)
        } catch (e: Exception) {
            println("Failed to save branching state: ${e.message}")
        }
    }

    override fun loadBranchingState(): String? {
        return try {
            val value = loadFromStorage(KEY_BRANCHING_STATE)
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            println("Failed to load branching state: ${e.message}")
            null
        }
    }

    override fun clearBranchingState() {
        try {
            saveToStorage(KEY_BRANCHING_STATE, "")
        } catch (e: Exception) {
            println("Failed to clear branching state: ${e.message}")
        }
    }

    override fun saveBranchMessages(branchId: String, messagesJson: String) {
        try {
            saveToStorage("${KEY_BRANCH_MESSAGES_PREFIX}${branchId}_messages", messagesJson)
        } catch (e: Exception) {
            println("Failed to save branch messages ($branchId): ${e.message}")
        }
    }

    override fun loadBranchMessages(branchId: String): String? {
        return try {
            val value = loadFromStorage("${KEY_BRANCH_MESSAGES_PREFIX}${branchId}_messages")
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            println("Failed to load branch messages ($branchId): ${e.message}")
            null
        }
    }

    override fun clearBranchMessages(branchId: String) {
        try {
            saveToStorage("${KEY_BRANCH_MESSAGES_PREFIX}${branchId}_messages", "")
        } catch (e: Exception) {
            println("Failed to clear branch messages ($branchId): ${e.message}")
        }
    }
}
