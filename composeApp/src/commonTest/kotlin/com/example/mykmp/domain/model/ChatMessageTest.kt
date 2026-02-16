package com.example.mykmp.domain.model

import com.example.mykmp.data.api.ClaudeMessageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageTest {

    @Test
    fun userMessageConvertsToApiFormat() {
        val chatMessage = ChatMessage(
            id = "1",
            role = ChatMessage.Role.USER,
            text = "Hello Claude",
            timestamp = 1000L
        )

        val apiMessage = ClaudeMessageRequest(
            role = "user",
            content = chatMessage.text
        )

        assertEquals("user", apiMessage.role)
        assertEquals("Hello Claude", apiMessage.content)
    }

    @Test
    fun assistantMessageConvertsToApiFormat() {
        val chatMessage = ChatMessage(
            id = "2",
            role = ChatMessage.Role.ASSISTANT,
            text = "Hello! How can I help?",
            timestamp = 2000L
        )

        val apiMessage = ClaudeMessageRequest(
            role = "assistant",
            content = chatMessage.text
        )

        assertEquals("assistant", apiMessage.role)
        assertEquals("Hello! How can I help?", apiMessage.content)
    }

    @Test
    fun defaultIsErrorIsFalse() {
        val message = ChatMessage(
            id = "1",
            role = ChatMessage.Role.ASSISTANT,
            text = "Normal message",
            timestamp = 1000L
        )

        assertFalse(message.isError)
    }

    @Test
    fun errorMessageFlagIsSet() {
        val errorMessage = ChatMessage(
            id = "3",
            role = ChatMessage.Role.ASSISTANT,
            text = "Error: something went wrong",
            timestamp = 3000L,
            isError = true
        )

        assertTrue(errorMessage.isError)
    }

    @Test
    fun conversationHistoryFiltersErrors() {
        val messages = listOf(
            ChatMessage("1", ChatMessage.Role.USER, "Hello", 1),
            ChatMessage("2", ChatMessage.Role.ASSISTANT, "Hi!", 2),
            ChatMessage("3", ChatMessage.Role.USER, "How are you?", 3),
            ChatMessage("4", ChatMessage.Role.ASSISTANT, "Error: timeout", 4, isError = true),
            ChatMessage("5", ChatMessage.Role.USER, "Try again", 5)
        )

        // Фильтруем ошибки и конвертируем в формат API
        val apiMessages = messages
            .filter { !it.isError }
            .map { msg ->
                ClaudeMessageRequest(
                    role = when (msg.role) {
                        ChatMessage.Role.USER -> "user"
                        ChatMessage.Role.ASSISTANT -> "assistant"
                    },
                    content = msg.text
                )
            }

        assertEquals(4, apiMessages.size) // 5 сообщений - 1 ошибка = 4
        assertEquals("user", apiMessages[0].role)
        assertEquals("Hello", apiMessages[0].content)
        assertEquals("assistant", apiMessages[1].role)
        assertEquals("Hi!", apiMessages[1].content)
        assertEquals("user", apiMessages[2].role)
        assertEquals("How are you?", apiMessages[2].content)
        assertEquals("user", apiMessages[3].role)
        assertEquals("Try again", apiMessages[3].content)
    }
}
