package com.example.mykmp.data.repository

import com.example.mykmp.data.storage.loadFromStorage
import com.example.mykmp.data.storage.saveToStorage
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.model.SavedSettings
import com.example.mykmp.domain.model.toConfig
import com.example.mykmp.domain.model.toSavedSettings
import com.example.mykmp.domain.repository.ChatHistoryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val KEY_HISTORY = "chat_history"
private const val KEY_SETTINGS = "chat_settings"

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
}
