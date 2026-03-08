package com.example.mykmp.domain.memory

/**
 * Интерфейс персистентного хранилища памяти.
 * Рабочая и долговременная память хранятся ОТДЕЛЬНО.
 */
interface MemoryRepository {
    // === Рабочая память ===
    fun saveTaskList(tasks: List<TaskMemory>)
    fun loadTaskList(): List<TaskMemory>

    // === Долговременная память ===
    fun saveFacts(facts: List<LongTermFact>)
    fun loadFacts(): List<LongTermFact>
}
