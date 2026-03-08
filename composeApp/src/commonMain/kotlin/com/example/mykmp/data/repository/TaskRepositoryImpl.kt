package com.example.mykmp.data.repository

import com.example.mykmp.database.TaskDatabase
import com.example.mykmp.domain.task.*
import io.ktor.util.date.*

class TaskRepositoryImpl(private val db: TaskDatabase) : TaskRepository {

    private val queries = db.taskDatabaseQueries

    override suspend fun getAllTasks(): List<Task> {
        return queries.getAllTasks().executeAsList().map { row ->
            buildTask(
                taskId = row.taskId,
                title = row.title,
                description = row.description,
                state = parseState(row.state),
                currentStep = row.currentStep?.let { parseStep(it) },
                progress = row.progress.toFloat(),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt
            )
        }
    }

    override suspend fun getTaskById(taskId: String): Task? {
        val row = queries.getTaskById(taskId).executeAsOneOrNull() ?: return null
        return buildTask(
            taskId = row.taskId,
            title = row.title,
            description = row.description,
            state = parseState(row.state),
            currentStep = row.currentStep?.let { parseStep(it) },
            progress = row.progress.toFloat(),
            createdAt = row.createdAt,
            updatedAt = row.updatedAt
        )
    }

    override suspend fun saveTask(task: Task) {
        queries.insertTask(
            taskId = task.taskId,
            title = task.title,
            description = task.description,
            state = task.state.name,
            currentStep = task.currentStep?.name,
            progress = task.progress.toDouble(),
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        )
        task.workingMemory.forEach { (k, v) ->
            queries.upsertMemory(task.taskId, k, v, getTimeMillis())
        }
        task.artifacts.forEach { artifact ->
            queries.insertArtifact(task.taskId, artifact.name, artifact.content, artifact.type, artifact.createdAt)
        }
    }

    override suspend fun updateTaskState(
        taskId: String,
        state: TaskStateEnum,
        step: TaskStepEnum?,
        progress: Float
    ) {
        queries.updateState(
            state = state.name,
            currentStep = step?.name,
            progress = progress.toDouble(),
            updatedAt = getTimeMillis(),
            taskId = taskId
        )
    }

    override suspend fun addTransition(taskId: String, transition: StateTransition) {
        queries.insertTransition(
            taskId = taskId,
            fromState = transition.from.name,
            toState = transition.to.name,
            step = transition.step?.name,
            reason = transition.reason,
            timestamp = transition.timestamp
        )
    }

    override suspend fun upsertWorkingMemory(taskId: String, key: String, value: String) {
        queries.upsertMemory(taskId, key, value, getTimeMillis())
    }

    override suspend fun deleteWorkingMemoryKey(taskId: String, key: String) {
        queries.deleteMemoryKey(taskId, key)
    }

    override suspend fun addArtifact(taskId: String, artifact: TaskArtifact) {
        queries.insertArtifact(taskId, artifact.name, artifact.content, artifact.type, artifact.createdAt)
    }

    override suspend fun deleteTask(taskId: String) {
        queries.deleteTask(taskId)
    }

    override suspend fun getWorkingMemory(taskId: String): Map<String, String> {
        return queries.getMemory(taskId).executeAsList()
            .associate { it.key to it.value_ }
    }

    override suspend fun getArtifacts(taskId: String): List<TaskArtifact> {
        return queries.getArtifacts(taskId).executeAsList().map { row ->
            TaskArtifact(
                name = row.name,
                content = row.content,
                type = row.type,
                createdAt = row.createdAt
            )
        }
    }

    override suspend fun getTransitions(taskId: String): List<StateTransition> {
        return queries.getTransitions(taskId).executeAsList().map { row ->
            StateTransition(
                from = parseState(row.fromState),
                to = parseState(row.toState),
                step = row.step?.let { parseStep(it) },
                reason = row.reason,
                timestamp = row.timestamp
            )
        }
    }

    // === Private ===

    private suspend fun buildTask(
        taskId: String,
        title: String,
        description: String,
        state: TaskStateEnum,
        currentStep: TaskStepEnum?,
        progress: Float,
        createdAt: Long,
        updatedAt: Long
    ): Task {
        return Task(
            taskId = taskId,
            title = title,
            description = description,
            state = state,
            currentStep = currentStep,
            progress = progress,
            workingMemory = getWorkingMemory(taskId),
            artifacts = getArtifacts(taskId),
            transitions = getTransitions(taskId),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseState(name: String): TaskStateEnum =
        try { TaskStateEnum.valueOf(name) } catch (_: Exception) { TaskStateEnum.IDLE }

    private fun parseStep(name: String): TaskStepEnum =
        try { TaskStepEnum.valueOf(name) } catch (_: Exception) { TaskStepEnum.RESEARCH }
}
