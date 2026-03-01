package com.example.mykmp.domain.model

import kotlinx.serialization.Serializable

/**
 * Резюме предыдущей части разговора.
 *
 * @param summaryText текст резюме
 * @param coveredMessageCount количество сообщений, покрытых этим резюме
 * @param createdAt timestamp создания
 * @param modelId ID модели, которая создала резюме
 */
@Serializable
data class ConversationSummary(
    val summaryText: String,
    val coveredMessageCount: Int,
    val createdAt: Long,
    val modelId: String
)
