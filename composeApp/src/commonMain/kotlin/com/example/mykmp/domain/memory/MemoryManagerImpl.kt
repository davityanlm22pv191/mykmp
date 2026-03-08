package com.example.mykmp.domain.memory

import io.ktor.util.date.getTimeMillis
import kotlin.random.Random

/**
 * Реализация MemoryManager.
 * In-memory кэш + персистентность через MemoryRepository.
 */
class MemoryManagerImpl(
    private val repository: MemoryRepository
) : MemoryManager {

    private val tasks = mutableListOf<TaskMemory>()
    private val facts = mutableListOf<LongTermFact>()

    init {
        // Загружаем данные из хранилища
        tasks.addAll(repository.loadTaskList())
        facts.addAll(repository.loadFacts())
    }

    // === Рабочая память ===

    override fun getTaskList(): List<TaskMemory> = tasks.toList()

    override fun getActiveTask(): TaskMemory? = tasks.find { it.isActive }

    override fun createTask(taskId: String, name: String): TaskMemory {
        val now = currentTimeMillis()
        val task = TaskMemory(
            taskId = taskId,
            name = name,
            createdAt = now,
            isActive = tasks.isEmpty() // первая задача — активна по умолчанию
        )
        tasks.add(task)
        saveTasks()
        return task
    }

    override fun setActiveTask(taskId: String) {
        val updated = tasks.map { it.copy(isActive = it.taskId == taskId) }
        tasks.clear()
        tasks.addAll(updated)
        saveTasks()
    }

    override fun addWorkingEntry(taskId: String, key: String, value: String) {
        val idx = tasks.indexOfFirst { it.taskId == taskId }
        if (idx == -1) return

        val task = tasks[idx]
        val now = currentTimeMillis()
        // Обновляем существующую запись или добавляем новую
        val existingIdx = task.entries.indexOfFirst { it.key == key }
        val newEntries = if (existingIdx != -1) {
            task.entries.toMutableList().apply {
                this[existingIdx] = WorkingMemoryEntry(key, value, now)
            }
        } else {
            task.entries + WorkingMemoryEntry(key, value, now)
        }

        tasks[idx] = task.copy(entries = newEntries)
        saveTasks()
    }

    override fun removeWorkingEntry(taskId: String, key: String) {
        val idx = tasks.indexOfFirst { it.taskId == taskId }
        if (idx == -1) return

        val task = tasks[idx]
        tasks[idx] = task.copy(entries = task.entries.filter { it.key != key })
        saveTasks()
    }

    override fun deleteTask(taskId: String) {
        tasks.removeAll { it.taskId == taskId }
        saveTasks()
    }

    // === Долговременная память ===

    override fun getAllFacts(): List<LongTermFact> = facts.toList()

    override fun getFactsByCategory(category: String): List<LongTermFact> {
        return facts.filter { it.category == category }
    }

    override fun searchFacts(query: String): List<LongTermFact> {
        if (query.isBlank()) return facts.toList()
        val q = query.lowercase()
        return facts.filter {
            it.key.lowercase().contains(q) ||
                    it.value.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
        }
    }

    override fun addFact(category: String, key: String, value: String, source: String) {
        val now = currentTimeMillis()
        val fact = LongTermFact(
            id = generateId(),
            category = category,
            key = key,
            value = value,
            confidence = if (source == "confirmed") 0.9f else 1.0f,
            source = source,
            createdAt = now
        )
        facts.add(fact)
        saveFacts()
    }

    override fun updateFact(factId: String, newValue: String) {
        val idx = facts.indexOfFirst { it.id == factId }
        if (idx == -1) return
        facts[idx] = facts[idx].copy(value = newValue)
        saveFacts()
    }

    override fun deleteFact(factId: String) {
        facts.removeAll { it.id == factId }
        saveFacts()
    }

    // === Контекст для Claude API ===

    override fun buildMemoryPrompt(): String? {
        val parts = mutableListOf<String>()

        // Рабочая задача (только активная)
        val activeTask = getActiveTask()
        if (activeTask != null && activeTask.entries.isNotEmpty()) {
            parts.add(buildWorkingPrompt(activeTask))
        }

        // Долговременные факты
        if (facts.isNotEmpty()) {
            parts.add(buildLongTermPrompt())
        }

        return parts.joinToString("\n\n").ifBlank { null }
    }

    // === Очистка ===

    override fun clearWorkingMemory(taskId: String) {
        val idx = tasks.indexOfFirst { it.taskId == taskId }
        if (idx == -1) return
        tasks[idx] = tasks[idx].copy(entries = emptyList())
        saveTasks()
    }

    override fun clearAllLongTerm() {
        facts.clear()
        saveFacts()
    }

    // === Private ===

    private fun buildWorkingPrompt(task: TaskMemory): String {
        return buildString {
            appendLine("[Рабочая задача: \"${task.name}\"]")
            for (entry in task.entries) {
                appendLine("${entry.key}: ${entry.value}")
            }
        }.trimEnd()
    }

    private fun buildLongTermPrompt(): String {
        return buildString {
            appendLine("[Долговременная память пользователя]")
            // Группируем по категориям
            val grouped = facts.groupBy { it.category }
            for ((category, categoryFacts) in grouped) {
                val label = try {
                    MemoryCategory.valueOf(category).displayName()
                } catch (_: Exception) {
                    category
                }
                appendLine("$label:")
                for (fact in categoryFacts) {
                    appendLine("  ${fact.key}: ${fact.value}")
                }
            }
        }.trimEnd()
    }

    private fun saveTasks() {
        repository.saveTaskList(tasks.toList())
    }

    private fun saveFacts() {
        repository.saveFacts(facts.toList())
    }

    private fun generateId(): String {
        // Простой UUID-подобный id без зависимости от java.util.UUID (для KMP)
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun currentTimeMillis(): Long = getTimeMillis()
}
