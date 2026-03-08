package com.example.mykmp.domain.task

enum class TaskStateEnum {
    IDLE, PLANNING, EXECUTION, VALIDATION, PAUSED, DONE, FAILED;

    fun displayName(): String = when (this) {
        IDLE       -> "Ожидание"
        PLANNING   -> "Планирование"
        EXECUTION  -> "Выполнение"
        VALIDATION -> "Проверка"
        PAUSED     -> "Пауза"
        DONE       -> "Завершено"
        FAILED     -> "Ошибка"
    }
}

enum class TaskStepEnum {
    RESEARCH, DESIGN, IMPLEMENT, TEST, DEPLOY, DOCUMENT;

    fun displayName(): String = when (this) {
        RESEARCH  -> "Исследование"
        DESIGN    -> "Проектирование"
        IMPLEMENT -> "Реализация"
        TEST      -> "Тестирование"
        DEPLOY    -> "Развёртывание"
        DOCUMENT  -> "Документирование"
    }

    fun next(): TaskStepEnum? = when (this) {
        RESEARCH  -> DESIGN
        DESIGN    -> IMPLEMENT
        IMPLEMENT -> TEST
        TEST      -> DEPLOY
        DEPLOY    -> DOCUMENT
        DOCUMENT  -> null
    }
}

data class Task(
    val taskId: String,
    val title: String,
    val description: String,
    val state: TaskStateEnum,
    val currentStep: TaskStepEnum?,
    val progress: Float,
    val workingMemory: Map<String, String>,
    val artifacts: List<TaskArtifact>,
    val transitions: List<StateTransition>,
    val createdAt: Long,
    val updatedAt: Long
)

data class TaskArtifact(
    val name: String,
    val content: String,
    val type: String,
    val createdAt: Long
)

data class StateTransition(
    val from: TaskStateEnum,
    val to: TaskStateEnum,
    val step: TaskStepEnum?,
    val reason: String,
    val timestamp: Long
)

data class NewTask(
    val title: String,
    val description: String
)

data class ExpectedAction(
    val nextState: TaskStateEnum,
    val nextStep: TaskStepEnum?,
    val description: String,
    val promptTemplate: String
)
