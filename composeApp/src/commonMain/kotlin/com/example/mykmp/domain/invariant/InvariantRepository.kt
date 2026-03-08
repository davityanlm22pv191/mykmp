package com.example.mykmp.domain.invariant

/**
 * Репозиторий инвариантов — хранит правила и зафиксированные нарушения.
 */
interface InvariantRepository {

    suspend fun getAllInvariants(): List<Invariant>

    suspend fun getGlobalInvariants(): List<Invariant>

    /**
     * Возвращает глобальные инварианты + инварианты конкретной задачи.
     */
    suspend fun getInvariantsForTask(taskId: String): List<Invariant>

    suspend fun saveInvariant(invariant: Invariant)

    suspend fun deleteInvariant(id: String)

    suspend fun saveViolation(violation: InvariantViolation)

    suspend fun getViolationsForMessage(messageId: String): List<InvariantViolation>
}
