package com.example.mykmp.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClaudeApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // JSON-конфигурация, аналогичная ClaudeApiClient
    private val jsonNoNulls = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun serializeRequest() {
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024,
            messages = listOf(
                ClaudeMessageRequest(role = "user", content = "Hello")
            )
        )
        val jsonString = json.encodeToString(ClaudeRequest.serializer(), request)

        // Проверяем, что поле max_tokens сериализуется в snake_case
        assert(jsonString.contains("\"max_tokens\":1024")) {
            "Expected max_tokens in JSON, got: $jsonString"
        }
        assert(jsonString.contains("\"model\":\"claude-sonnet-4-20250514\""))
        assert(jsonString.contains("\"role\":\"user\""))
        assert(jsonString.contains("\"content\":\"Hello\""))
    }

    @Test
    fun deserializeSuccessResponse() {
        val responseJson = """
        {
            "id": "msg_01XFDUDYJgAACzvnptvVoYEL",
            "type": "message",
            "role": "assistant",
            "content": [
                {
                    "type": "text",
                    "text": "Hello! How can I help you today?"
                }
            ],
            "model": "claude-sonnet-4-20250514",
            "stop_reason": "end_turn",
            "usage": {
                "input_tokens": 12,
                "output_tokens": 25
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<ClaudeResponse>(responseJson)

        assertEquals("msg_01XFDUDYJgAACzvnptvVoYEL", response.id)
        assertEquals("message", response.type)
        assertEquals("assistant", response.role)
        assertEquals(1, response.content.size)
        assertEquals("text", response.content[0].type)
        assertEquals("Hello! How can I help you today?", response.content[0].text)
        assertEquals("claude-sonnet-4-20250514", response.model)
        assertEquals("end_turn", response.stopReason)
        assertNotNull(response.usage)
        assertEquals(12, response.usage?.inputTokens)
        assertEquals(25, response.usage?.outputTokens)
    }

    @Test
    fun deserializeResponseWithoutOptionalFields() {
        val responseJson = """
        {
            "id": "msg_123",
            "type": "message",
            "role": "assistant",
            "content": [{"type": "text", "text": "Hi"}],
            "model": "claude-sonnet-4-20250514"
        }
        """.trimIndent()

        val response = json.decodeFromString<ClaudeResponse>(responseJson)

        assertEquals("msg_123", response.id)
        assertNull(response.stopReason)
        assertNull(response.usage)
    }

    @Test
    fun deserializeResponseIgnoresUnknownFields() {
        val responseJson = """
        {
            "id": "msg_456",
            "type": "message",
            "role": "assistant",
            "content": [{"type": "text", "text": "Hi"}],
            "model": "claude-sonnet-4-20250514",
            "some_future_field": "should be ignored",
            "another_field": 42
        }
        """.trimIndent()

        val response = json.decodeFromString<ClaudeResponse>(responseJson)
        assertEquals("msg_456", response.id)
        assertEquals("Hi", response.content[0].text)
    }

    @Test
    fun deserializeErrorResponse() {
        val errorJson = """
        {
            "type": "error",
            "error": {
                "type": "invalid_request_error",
                "message": "Invalid API key provided"
            }
        }
        """.trimIndent()

        val errorResponse = json.decodeFromString<ClaudeErrorResponse>(errorJson)

        assertEquals("error", errorResponse.type)
        assertEquals("invalid_request_error", errorResponse.error.type)
        assertEquals("Invalid API key provided", errorResponse.error.message)
    }

    @Test
    fun serializeRequestWithSystemAndStopSequences() {
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 2048,
            messages = listOf(
                ClaudeMessageRequest(role = "user", content = "Hello")
            ),
            system = "You are a helpful assistant.",
            stopSequences = listOf("END", "STOP")
        )
        val jsonString = jsonNoNulls.encodeToString(ClaudeRequest.serializer(), request)

        assert(jsonString.contains("\"system\":\"You are a helpful assistant.\"")) {
            "Expected system field in JSON, got: $jsonString"
        }
        assert(jsonString.contains("\"stop_sequences\":[\"END\",\"STOP\"]")) {
            "Expected stop_sequences field in JSON, got: $jsonString"
        }
    }

    @Test
    fun serializeRequestNullFieldsOmitted() {
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024,
            messages = listOf(
                ClaudeMessageRequest(role = "user", content = "Hi")
            )
            // system и stopSequences по умолчанию null
        )
        val jsonString = jsonNoNulls.encodeToString(ClaudeRequest.serializer(), request)

        assert(!jsonString.contains("system")) {
            "system=null should be omitted, got: $jsonString"
        }
        assert(!jsonString.contains("stop_sequences")) {
            "stop_sequences=null should be omitted, got: $jsonString"
        }
    }

    @Test
    fun deserializeMultipleContentBlocks() {
        val responseJson = """
        {
            "id": "msg_789",
            "type": "message",
            "role": "assistant",
            "content": [
                {"type": "text", "text": "First paragraph."},
                {"type": "text", "text": "Second paragraph."}
            ],
            "model": "claude-sonnet-4-20250514"
        }
        """.trimIndent()

        val response = json.decodeFromString<ClaudeResponse>(responseJson)

        assertEquals(2, response.content.size)
        assertEquals("First paragraph.", response.content[0].text)
        assertEquals("Second paragraph.", response.content[1].text)
    }
}
