package com.example.mykmp.domain.task

interface TaskRepository {
    suspend fun getAllTasks(): List<Task>
    suspend fun getTaskById(taskId: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun updateTaskState(taskId: String, state: TaskStateEnum, step: TaskStepEnum?, progress: Float)
    suspend fun addTransition(taskId: String, transition: StateTransition)
    suspend fun upsertWorkingMemory(taskId: String, key: String, value: String)
    suspend fun deleteWorkingMemoryKey(taskId: String, key: String)
    suspend fun addArtifact(taskId: String, artifact: TaskArtifact)
    suspend fun deleteTask(taskId: String)
    suspend fun getWorkingMemory(taskId: String): Map<String, String>
    suspend fun getArtifacts(taskId: String): List<TaskArtifact>
    suspend fun getTransitions(taskId: String): List<StateTransition>
}
