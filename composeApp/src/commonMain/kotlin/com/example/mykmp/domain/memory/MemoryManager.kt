package com.example.mykmp.domain.memory

/**
 * Единый интерфейс управления памятью ассистента.
 *
 * Краткосрочная память (Short-term) = существующий List<ChatMessage> + ContextManager.
 * Этот интерфейс управляет только:
 * - Рабочей памятью (Working) — данные текущей задачи
 * - Долговременной памятью (Long-term) — профиль, решения, знания
 */
interface MemoryManager {

    // === Рабочая память (Working Memory) ===

    fun getTaskList(): List<TaskMemory>
    fun getActiveTask(): TaskMemory?
    fun createTask(taskId: String, name: String): TaskMemory
    fun setActiveTask(taskId: String)
    fun addWorkingEntry(taskId: String, key: String, value: String)
    fun removeWorkingEntry(taskId: String, key: String)
    fun deleteTask(taskId: String)

    // === Долговременная память (Long-term Memory) ===

    fun getAllFacts(): List<LongTermFact>
    fun getFactsByCategory(category: String): List<LongTermFact>
    fun searchFacts(query: String): List<LongTermFact>
    fun addFact(category: String, key: String, value: String, source: String = "manual")
    fun updateFact(factId: String, newValue: String)
    fun deleteFact(factId: String)

    // === Контекст для Claude API ===

    /**
     * Формирует дополнение к system prompt из рабочей задачи и долговременных знаний.
     * Возвращает null, если память пуста.
     */
    fun buildMemoryPrompt(): String?

    // === Очистка ===

    fun clearWorkingMemory(taskId: String)
    fun clearAllLongTerm()
}
