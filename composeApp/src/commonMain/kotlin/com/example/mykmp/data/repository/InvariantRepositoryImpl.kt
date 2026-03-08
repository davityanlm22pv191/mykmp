package com.example.mykmp.data.repository

import com.example.mykmp.database.TaskDatabase
import com.example.mykmp.domain.invariant.*

/**
 * SQLDelight-реализация репозитория инвариантов.
 * Паттерн аналогичен TaskRepositoryImpl.
 */
class InvariantRepositoryImpl(private val db: TaskDatabase) : InvariantRepository {

    private val queries = db.taskDatabaseQueries

    override suspend fun getAllInvariants(): List<Invariant> =
        queries.getAllInvariants().executeAsList().map { it.toInvariant() }

    override suspend fun getGlobalInvariants(): List<Invariant> =
        queries.getGlobalInvariants().executeAsList().map { it.toInvariant() }

    override suspend fun getInvariantsForTask(taskId: String): List<Invariant> =
        queries.getInvariantsForTask(taskId).executeAsList().map { it.toInvariant() }

    override suspend fun saveInvariant(invariant: Invariant) {
        queries.insertInvariant(
            id = invariant.id,
            type = invariant.type.name,
            constraintText = invariant.constraintText,
            description = invariant.description,
            priority = invariant.priority.name,
            taskId = invariant.taskId,
            createdAt = invariant.createdAt,
            source = invariant.source
        )
    }

    override suspend fun deleteInvariant(id: String) {
        queries.deleteInvariant(id)
    }

    override suspend fun saveViolation(violation: InvariantViolation) {
        queries.insertViolation(
            invariantId = violation.invariantId,
            messageId = violation.messageId,
            violationReason = violation.violationReason,
            severity = violation.severity.name,
            timestamp = violation.timestamp
        )
    }

    override suspend fun getViolationsForMessage(messageId: String): List<InvariantViolation> =
        queries.getViolationsForMessage(messageId).executeAsList().map { row ->
            InvariantViolation(
                invariantId = row.invariantId,
                messageId = row.messageId,
                violationReason = row.violationReason,
                severity = parsePriority(row.severity),
                timestamp = row.timestamp
            )
        }

    // === Private ===

    private fun com.example.mykmp.database.Invariants.toInvariant() = Invariant(
        id = id,
        type = parseType(type),
        constraintText = constraintText,
        description = description,
        priority = parsePriority(priority),
        taskId = taskId,
        createdAt = createdAt,
        source = source
    )

    private fun parseType(name: String): InvariantType =
        try { InvariantType.valueOf(name) } catch (_: Exception) { InvariantType.BUSINESS_RULES }

    private fun parsePriority(name: String): ConstraintPriority =
        try { ConstraintPriority.valueOf(name) } catch (_: Exception) { ConstraintPriority.MEDIUM }
}
