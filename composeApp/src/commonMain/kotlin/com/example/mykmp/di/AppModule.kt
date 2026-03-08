package com.example.mykmp.di

import com.example.mykmp.config.getApiKey
import com.example.mykmp.data.api.ClaudeApiClient
import com.example.mykmp.data.database.DatabaseDriverFactory
import com.example.mykmp.data.repository.*
import com.example.mykmp.database.TaskDatabase
import com.example.mykmp.domain.context.ContextManager
import com.example.mykmp.domain.memory.MemoryManager
import com.example.mykmp.domain.memory.MemoryManagerImpl
import com.example.mykmp.domain.memory.MemoryRepository
import com.example.mykmp.domain.profile.ProfileManager
import com.example.mykmp.domain.profile.ProfileManagerImpl
import com.example.mykmp.domain.profile.ProfileRepository
import com.example.mykmp.domain.repository.ChatHistoryRepository
import com.example.mykmp.domain.repository.ChatRepository
import com.example.mykmp.domain.task.TaskRepository
import com.example.mykmp.domain.task.TaskStateMachine
import com.example.mykmp.domain.task.TaskStateMachineImpl
import com.example.mykmp.presentation.ChatViewModel
import com.example.mykmp.presentation.TaskDashboardViewModel

/**
 * Простой ручной DI-контейнер.
 *
 * Создаёт и хранит синглтоны сетевого и доменного слоёв.
 * ViewModel создаётся как новый экземпляр при каждом вызове
 * (управление жизненным циклом — на стороне Compose viewModel {}).
 *
 * Для продакшена рекомендуется перейти на Koin или аналогичный DI-фреймворк.
 */
object AppModule {

    private val apiKey: String by lazy { getApiKey() }

    private val claudeApiClient: ClaudeApiClient by lazy {
        ClaudeApiClient(apiKey = apiKey)
    }

    private val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(claudeApiClient)
    }

    private val chatHistoryRepository: ChatHistoryRepository by lazy {
        ChatHistoryRepositoryImpl()
    }

    private val contextManager: ContextManager by lazy {
        ContextManager(chatRepository, chatHistoryRepository)
    }

    private val memoryRepository: MemoryRepository by lazy {
        MemoryRepositoryImpl()
    }

    private val memoryManager: MemoryManager by lazy {
        MemoryManagerImpl(memoryRepository)
    }

    private val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl()
    }

    private val profileManager: ProfileManager by lazy {
        ProfileManagerImpl(profileRepository)
    }

    private val driverFactory: DatabaseDriverFactory by lazy {
        DatabaseDriverFactory()
    }

    private val taskDatabase: TaskDatabase? by lazy {
        try { TaskDatabase(driverFactory.createDriver()) } catch (e: Exception) {
            println("⚠️ SQLDelight не поддерживается на этой платформе: ${e.message}")
            null
        }
    }

    private val taskRepository: TaskRepository by lazy {
        val db = taskDatabase ?: throw IllegalStateException("TaskDatabase недоступна")
        TaskRepositoryImpl(db)
    }

    private val taskStateMachine: TaskStateMachine by lazy {
        TaskStateMachineImpl(taskRepository)
    }

    /**
     * Создаёт новый экземпляр ChatViewModel.
     */
    fun createChatViewModel(): ChatViewModel {
        return ChatViewModel(
            chatRepository, chatHistoryRepository, contextManager,
            memoryManager, profileManager, taskStateMachine
        )
    }

    /**
     * Создаёт новый экземпляр TaskDashboardViewModel.
     */
    fun createTaskDashboardViewModel(): TaskDashboardViewModel {
        return TaskDashboardViewModel(taskStateMachine, taskRepository)
    }
}
