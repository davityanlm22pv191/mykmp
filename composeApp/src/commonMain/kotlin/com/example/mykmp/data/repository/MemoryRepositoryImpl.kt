package com.example.mykmp.data.repository

import com.example.mykmp.data.storage.loadFromStorage
import com.example.mykmp.data.storage.saveToStorage
import com.example.mykmp.domain.memory.LongTermFact
import com.example.mykmp.domain.memory.MemoryRepository
import com.example.mykmp.domain.memory.TaskMemory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val KEY_TASKS = "memory_tasks"
private const val KEY_LONG_TERM = "memory_long_term"

/**
 * Реализация MemoryRepository через saveToStorage.
 * Рабочая и долговременная память хранятся под разными ключами.
 */
class MemoryRepositoryImpl : MemoryRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // === Рабочая память ===

    override fun saveTaskList(tasks: List<TaskMemory>) {
        try {
            saveToStorage(KEY_TASKS, json.encodeToString(tasks))
        } catch (e: Exception) {
            println("Failed to save task list: ${e.message}")
        }
    }

    override fun loadTaskList(): List<TaskMemory> {
        return try {
            val jsonString = loadFromStorage(KEY_TASKS) ?: return emptyList()
            if (jsonString.isBlank()) return emptyList()
            json.decodeFromString<List<TaskMemory>>(jsonString)
        } catch (e: Exception) {
            println("Failed to load task list: ${e.message}")
            emptyList()
        }
    }

    // === Долговременная память ===

    override fun saveFacts(facts: List<LongTermFact>) {
        try {
            saveToStorage(KEY_LONG_TERM, json.encodeToString(facts))
        } catch (e: Exception) {
            println("Failed to save long-term facts: ${e.message}")
        }
    }

    override fun loadFacts(): List<LongTermFact> {
        return try {
            val jsonString = loadFromStorage(KEY_LONG_TERM) ?: return emptyList()
            if (jsonString.isBlank()) return emptyList()
            json.decodeFromString<List<LongTermFact>>(jsonString)
        } catch (e: Exception) {
            println("Failed to load long-term facts: ${e.message}")
            emptyList()
        }
    }
}
