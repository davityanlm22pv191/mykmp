package com.example.mykmp.domain.task

import com.example.mykmp.domain.task.TaskStateEnum.*
import com.example.mykmp.domain.task.TaskStepEnum.*
import io.ktor.util.date.*
import kotlin.random.Random

class TaskStateMachineImpl(private val repository: TaskRepository) : TaskStateMachine {

    private var _activeTask: Task? = null
    override val activeTask: Task? get() = _activeTask

    override var onStateChanged: ((Task?) -> Unit)? = null
    override var onSuggestionReady: ((ExpectedAction) -> Unit)? = null

    // Допустимые переходы из каждого состояния
    private val allowedTransitions = mapOf(
        IDLE       to setOf(PLANNING),
        PLANNING   to setOf(EXECUTION),
        EXECUTION  to setOf(VALIDATION, PAUSED),
        PAUSED     to setOf(EXECUTION),
        VALIDATION to setOf(DONE, FAILED),
        DONE       to setOf(IDLE),
        FAILED     to setOf(IDLE)
    )

    // === Управление жизненным циклом ===

    override suspend fun startTask(newTask: NewTask): Task {
        val now = getTimeMillis()
        val task = Task(
            taskId = generateId(),
            title = newTask.title,
            description = newTask.description,
            state = PLANNING,
            currentStep = null,
            progress = 0f,
            workingMemory = emptyMap(),
            artifacts = emptyList(),
            transitions = listOf(
                StateTransition(IDLE, PLANNING, null, "Задача создана", now)
            ),
            createdAt = now,
            updatedAt = now
        )
        repository.saveTask(task)
        _activeTask = task
        onStateChanged?.invoke(_activeTask)
        return task
    }

    override suspend fun advance(reason: String): Task {
        val task = _activeTask ?: error("Нет активной задачи")
        val nextState = when (task.state) {
            PLANNING   -> EXECUTION
            EXECUTION  -> VALIDATION
            VALIDATION -> DONE
            DONE       -> IDLE
            FAILED     -> IDLE
            else       -> error("Нельзя продвинуть из состояния ${task.state}")
        }
        val nextStep: TaskStepEnum? = when {
            nextState == EXECUTION && task.state == PLANNING -> RESEARCH
            nextState == EXECUTION -> task.currentStep?.next() ?: RESEARCH
            else -> null
        }
        return doTransition(task, nextState, nextStep, reason.ifBlank { "Продвижение вперёд" })
    }

    override suspend fun pause(reason: String): Task {
        val task = _activeTask ?: error("Нет активной задачи")
        check(task.state == EXECUTION) { "Пауза возможна только из EXECUTION" }
        return doTransition(task, PAUSED, task.currentStep, reason.ifBlank { "Пауза" })
    }

    override suspend fun resume(): Task {
        val task = _activeTask ?: error("Нет активной задачи")
        check(task.state == PAUSED) { "Возобновление возможно только из PAUSED" }
        return doTransition(task, EXECUTION, task.currentStep, "Возобновление")
    }

    override suspend fun fail(reason: String): Task {
        val task = _activeTask ?: error("Нет активной задачи")
        return doTransition(task, FAILED, task.currentStep, reason.ifBlank { "Ошибка" })
    }

    override suspend fun complete(): Task {
        val task = _activeTask ?: error("Нет активной задачи")
        check(task.state == VALIDATION) { "Завершение возможно только из VALIDATION" }
        return doTransition(task, DONE, null, "Задача выполнена")
    }

    override suspend fun setActiveTask(taskId: String) {
        _activeTask = repository.getTaskById(taskId)
        onStateChanged?.invoke(_activeTask)
    }

    override suspend fun reset() {
        _activeTask = null
        onStateChanged?.invoke(null)
    }

    // === Контекст для Claude ===

    override fun getSystemPromptAddition(): String? {
        val task = _activeTask ?: return null
        if (task.state == IDLE || task.state == DONE || task.state == FAILED) return null
        return buildString {
            appendLine("[Текущая задача: \"${task.title}\"]")
            appendLine("Состояние: ${task.state.displayName()}${task.currentStep?.let { " → ${it.displayName()}" } ?: ""}")
            appendLine("Прогресс: ${(task.progress * 100).toInt()}%")
            if (task.workingMemory.isNotEmpty()) {
                appendLine("Рабочая память:")
                task.workingMemory.forEach { (k, v) -> appendLine("  $k: $v") }
            }
            when (task.state) {
                PLANNING   -> appendLine("Сейчас: составить план решения задачи.")
                EXECUTION  -> appendLine("Сейчас: выполнить шаг ${task.currentStep?.displayName() ?: ""}.")
                VALIDATION -> appendLine("Сейчас: проверить результат на соответствие требованиям.")
                PAUSED     -> appendLine("Задача на паузе. При возобновлении продолжить с шага ${task.currentStep?.displayName() ?: ""}.")
                else -> {}
            }
        }.trimEnd()
    }

    override suspend fun onMessageReceived(responseText: String) {
        val task = _activeTask ?: return

        // Извлекаем code-блоки → артефакты
        val codeBlocks = extractCodeBlocks(responseText)
        codeBlocks.forEachIndexed { i, code ->
            val artifact = TaskArtifact(
                name = "artifact_${task.artifacts.size + i + 1}",
                content = code,
                type = "code",
                createdAt = getTimeMillis()
            )
            repository.addArtifact(task.taskId, artifact)
        }

        // Определяем сигнал из ответа
        val isDone = responseText.containsAnyOf("готово", "завершено", "реализовано", "✅", "done", "complete", "выполнено")
        val isFailed = responseText.containsAnyOf("ошибка", "не работает", "failed", "error", "провал")

        val next = getNextExpectedAction()
        when {
            isDone && next != null -> onSuggestionReady?.invoke(next)
            isFailed              -> onSuggestionReady?.invoke(
                ExpectedAction(FAILED, null, "Завершить задачу с ошибкой", "")
            )
        }

        // Обновляем кэш
        _activeTask = repository.getTaskById(task.taskId)
        onStateChanged?.invoke(_activeTask)
    }

    override fun getNextExpectedAction(): ExpectedAction? {
        val task = _activeTask ?: return null
        return when (task.state) {
            PLANNING -> ExpectedAction(
                nextState = EXECUTION,
                nextStep = RESEARCH,
                description = "Начать выполнение: ${RESEARCH.displayName()}",
                promptTemplate = "Выполни шаг ${RESEARCH.displayName()} для задачи \"${task.title}\"."
            )
            EXECUTION -> {
                val nextStep = task.currentStep?.next()
                if (nextStep != null) {
                    ExpectedAction(
                        nextState = EXECUTION,
                        nextStep = nextStep,
                        description = "Следующий шаг: ${nextStep.displayName()}",
                        promptTemplate = "Выполни шаг ${nextStep.displayName()} для задачи \"${task.title}\"."
                    )
                } else {
                    ExpectedAction(
                        nextState = VALIDATION,
                        nextStep = null,
                        description = "Перейти к проверке",
                        promptTemplate = "Проверь результат задачи \"${task.title}\" на соответствие требованиям."
                    )
                }
            }
            VALIDATION -> ExpectedAction(
                nextState = DONE,
                nextStep = null,
                description = "Завершить задачу",
                promptTemplate = ""
            )
            else -> null
        }
    }

    // === Private ===

    private suspend fun doTransition(
        task: Task,
        newState: TaskStateEnum,
        newStep: TaskStepEnum?,
        reason: String
    ): Task {
        val now = getTimeMillis()
        val progress = calcProgress(newState, newStep)

        repository.updateTaskState(task.taskId, newState, newStep, progress)
        repository.addTransition(
            task.taskId,
            StateTransition(task.state, newState, newStep, reason, now)
        )

        _activeTask = repository.getTaskById(task.taskId)
        onStateChanged?.invoke(_activeTask)
        return _activeTask ?: task
    }

    private fun calcProgress(state: TaskStateEnum, step: TaskStepEnum?): Float = when (state) {
        IDLE       -> 0f
        PLANNING   -> 0.05f
        EXECUTION  -> when (step) {
            RESEARCH  -> 0.15f
            DESIGN    -> 0.30f
            IMPLEMENT -> 0.50f
            TEST      -> 0.70f
            DEPLOY    -> 0.85f
            DOCUMENT  -> 0.95f
            null      -> 0.10f
        }
        VALIDATION -> 0.97f
        DONE       -> 1.0f
        FAILED     -> _activeTask?.progress ?: 0f
        PAUSED     -> _activeTask?.progress ?: 0f
    }

    private fun extractCodeBlocks(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = text.indexOf("```")
        while (start != -1) {
            val langEnd = text.indexOf('\n', start + 3)
            if (langEnd == -1) break
            val end = text.indexOf("```", langEnd + 1)
            if (end == -1) break
            result.add(text.substring(langEnd + 1, end).trim())
            start = text.indexOf("```", end + 3)
        }
        return result
    }

    private fun String.containsAnyOf(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
