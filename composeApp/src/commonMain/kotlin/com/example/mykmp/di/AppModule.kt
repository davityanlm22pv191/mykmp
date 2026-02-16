package com.example.mykmp.di

import com.example.mykmp.config.getApiKey
import com.example.mykmp.data.api.ClaudeApiClient
import com.example.mykmp.data.repository.ChatRepositoryImpl
import com.example.mykmp.domain.repository.ChatRepository
import com.example.mykmp.presentation.ChatViewModel

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

    /**
     * Создаёт новый экземпляр ChatViewModel.
     */
    fun createChatViewModel(): ChatViewModel {
        return ChatViewModel(chatRepository)
    }
}
