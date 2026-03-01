package com.example.mykmp.domain.context

import com.example.mykmp.data.api.ClaudeMessageRequest
import com.example.mykmp.domain.model.ChatMessage
import com.example.mykmp.domain.model.ChatRequestConfig
import com.example.mykmp.domain.repository.ChatHistoryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ветка разговора.
 */
@Serializable
data class ConversationBranch(
    val id: String,
    val name: String,
    val parentBranchId: String? = null,
    val forkMessageIndex: Int = 0,
    val createdAt: Long = 0L
)

/**
 * Состояние ветвления: список веток + активная ветка + настройки.
 */
@Serializable
data class BranchingState(
    val branches: List<ConversationBranch> = listOf(
        ConversationBranch(id = "main", name = "Основная")
    ),
    val activeBranchId: String = "main",
    val autoCheckpointInterval: Int = 20,
    val messagesSinceLastCheckpoint: Int = 0
)

/**
 * Стратегия ветвления: создаёт точки ветвления, каждая ветка — независимый поток.
 * Main ветка использует основную chat_history.
 * Дополнительные ветки хранят сообщения после fork в отдельных ключах.
 *
 * При buildContext: prefix (общие сообщения до fork) + branch-specific messages,
 * отправляются последние N из объединённого потока.
 */
class BranchingStrategy(
    private val historyRepository: ChatHistoryRepository
) : ContextStrategy {

    override val type = ContextStrategyType.BRANCHING
    override val displayName = type.displayName()
    override val description = type.description()

    private var state = BranchingState()
    // Кэш сообщений для не-main веток
    private val branchMessagesCache = mutableMapOf<String, List<ChatMessage>>()

    val currentState: BranchingState get() = state
    val branches: List<ConversationBranch> get() = state.branches
    val activeBranchId: String get() = state.activeBranchId
    val activeBranch: ConversationBranch
        get() = state.branches.find { it.id == state.activeBranchId }
            ?: state.branches.first()

    /** Callback для уведомления UI об изменении состояния. */
    var onStateChanged: (() -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun buildContext(
        messages: List<ChatMessage>,
        config: ChatRequestConfig
    ): ContextResult {
        val effectiveMessages = resolveMessages(messages)
        val validMessages = effectiveMessages.filter { !it.isError }
        val window = validMessages.takeLast(config.contextWindowSize)

        val apiMessages = window.map { msg ->
            ClaudeMessageRequest(
                role = when (msg.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                },
                content = msg.text
            )
        }

        return ContextResult(
            messagesToSend = apiMessages,
            systemPromptAddition = null
        )
    }

    override suspend fun onMessageReceived(
        allMessages: List<ChatMessage>,
        config: ChatRequestConfig
    ) {
        // Если мы в не-main ветке, сохраняем сообщение в кэш ветки
        if (state.activeBranchId != "main") {
            val branch = activeBranch
            val branchMessages = allMessages.drop(branch.forkMessageIndex)
            branchMessagesCache[state.activeBranchId] = branchMessages
            saveBranchMessages(state.activeBranchId, branchMessages)
        }

        // Проверяем авто-checkpoint
        val newCount = state.messagesSinceLastCheckpoint + 1
        if (newCount >= state.autoCheckpointInterval) {
            // Авто-checkpoint: записываем точку, но не создаём ветку автоматически
            state = state.copy(messagesSinceLastCheckpoint = 0)
            saveState()
            println("📌 Авто-checkpoint на ${allMessages.size} сообщениях")
        } else {
            state = state.copy(messagesSinceLastCheckpoint = newCount)
            saveState()
        }
    }

    override fun loadState() {
        state = historyRepository.loadBranchingState()?.let { jsonStr ->
            try {
                json.decodeFromString<BranchingState>(jsonStr)
            } catch (e: Exception) {
                println("❌ Ошибка загрузки branching state: ${e.message}")
                BranchingState()
            }
        } ?: BranchingState()

        // Загрузить кэш сообщений для не-main веток
        branchMessagesCache.clear()
        for (branch in state.branches) {
            if (branch.id != "main") {
                loadBranchMessages(branch.id)?.let {
                    branchMessagesCache[branch.id] = it
                }
            }
        }
    }

    override fun clearState() {
        // Очистить все branch messages
        for (branch in state.branches) {
            if (branch.id != "main") {
                historyRepository.clearBranchMessages(branch.id)
            }
        }
        branchMessagesCache.clear()
        state = BranchingState()
        historyRepository.clearBranchingState()
    }

    /**
     * Создаёт новую ветку от текущего момента (или указанного индекса).
     * @param name Имя ветки
     * @param mainMessages Текущие сообщения main ветки
     * @param forkIndex Индекс, с которого создать ветку (default: текущая длина)
     */
    fun createBranch(
        name: String,
        mainMessages: List<ChatMessage>,
        forkIndex: Int = mainMessages.size
    ): ConversationBranch {
        val branchId = "branch_${state.branches.size}_${forkIndex}"
        val branch = ConversationBranch(
            id = branchId,
            name = name,
            parentBranchId = state.activeBranchId,
            forkMessageIndex = forkIndex,
            createdAt = 0L
        )

        // Если fork в середине, копируем сообщения после fork в ветку
        val afterFork = mainMessages.drop(forkIndex)
        if (afterFork.isNotEmpty()) {
            branchMessagesCache[branchId] = afterFork
            saveBranchMessages(branchId, afterFork)
        } else {
            branchMessagesCache[branchId] = emptyList()
        }

        state = state.copy(
            branches = state.branches + branch,
            activeBranchId = branchId,
            messagesSinceLastCheckpoint = 0
        )
        saveState()
        onStateChanged?.invoke()

        println("🌿 Создана ветка '$name' (fork@$forkIndex)")
        return branch
    }

    /**
     * Переключает активную ветку.
     */
    fun switchBranch(branchId: String) {
        if (branchId == state.activeBranchId) return
        if (state.branches.none { it.id == branchId }) return

        state = state.copy(
            activeBranchId = branchId,
            messagesSinceLastCheckpoint = 0
        )
        saveState()
        onStateChanged?.invoke()
        println("🔀 Переключено на ветку '${activeBranch.name}'")
    }

    /**
     * Удаляет ветку (нельзя удалить main).
     */
    fun deleteBranch(branchId: String) {
        if (branchId == "main") return
        if (state.branches.none { it.id == branchId }) return

        branchMessagesCache.remove(branchId)
        historyRepository.clearBranchMessages(branchId)

        val newBranches = state.branches.filter { it.id != branchId }
        val newActiveId = if (state.activeBranchId == branchId) "main" else state.activeBranchId

        state = state.copy(
            branches = newBranches,
            activeBranchId = newActiveId
        )
        saveState()
        onStateChanged?.invoke()
        println("🗑 Удалена ветка '$branchId'")
    }

    /**
     * Собирает эффективные сообщения для текущей ветки.
     * Main → все сообщения как есть.
     * Не-main → prefix (до fork) + branch messages.
     */
    fun resolveMessages(mainMessages: List<ChatMessage>): List<ChatMessage> {
        if (state.activeBranchId == "main") {
            return mainMessages
        }

        val branch = activeBranch
        val prefix = mainMessages.take(branch.forkMessageIndex)
        val branchMessages = branchMessagesCache[state.activeBranchId] ?: emptyList()
        return prefix + branchMessages
    }

    // --- Persistence ---

    private fun saveState() {
        try {
            val jsonStr = json.encodeToString(BranchingState.serializer(), state)
            historyRepository.saveBranchingState(jsonStr)
        } catch (e: Exception) {
            println("❌ Ошибка сохранения branching state: ${e.message}")
        }
    }

    private fun saveBranchMessages(branchId: String, messages: List<ChatMessage>) {
        try {
            val jsonStr = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ChatMessage.serializer()),
                messages
            )
            historyRepository.saveBranchMessages(branchId, jsonStr)
        } catch (e: Exception) {
            println("❌ Ошибка сохранения branch messages: ${e.message}")
        }
    }

    private fun loadBranchMessages(branchId: String): List<ChatMessage>? {
        return try {
            historyRepository.loadBranchMessages(branchId)?.let { jsonStr ->
                json.decodeFromString<List<ChatMessage>>(jsonStr)
            }
        } catch (e: Exception) {
            println("❌ Ошибка загрузки branch messages: ${e.message}")
            null
        }
    }
}
