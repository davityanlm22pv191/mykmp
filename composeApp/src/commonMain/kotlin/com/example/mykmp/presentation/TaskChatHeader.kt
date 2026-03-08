package com.example.mykmp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mykmp.domain.task.ExpectedAction
import com.example.mykmp.domain.task.Task
import com.example.mykmp.domain.task.TaskStateEnum

/**
 * Заголовок активной задачи в чате.
 * Показывается над списком сообщений, когда есть активная задача.
 */
@Composable
fun TaskChatHeader(
    task: Task,
    hasSuggestion: Boolean,
    suggestion: ExpectedAction?,
    onAdvance: () -> Unit,
    onPause: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // State badge
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = task.state.badgeColor()
                    ) {
                        Text(
                            text = task.state.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (task.currentStep != null) {
                        Text(
                            text = "→ ${task.currentStep.displayName()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onOpenDashboard,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("📋", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )

            // Suggestion banner
            if (hasSuggestion && suggestion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Следующий шаг: ${suggestion.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onDismissSuggestion,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("✕", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onAdvance,
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("Продвинуть →", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (task.state == TaskStateEnum.EXECUTION) {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("⏸ Пауза", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (task.state == TaskStateEnum.PAUSED) {
                        OutlinedButton(
                            onClick = onAdvance,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("▶ Продолжить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (task.state in setOf(TaskStateEnum.PLANNING, TaskStateEnum.EXECUTION, TaskStateEnum.VALIDATION)) {
                        OutlinedButton(
                            onClick = onAdvance,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("→ Далее", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun TaskStateEnum.badgeColor(): Color = when (this) {
    TaskStateEnum.PLANNING   -> Color(0xFFF59E0B)
    TaskStateEnum.EXECUTION  -> Color(0xFF3B82F6)
    TaskStateEnum.VALIDATION -> Color(0xFFF97316)
    TaskStateEnum.PAUSED     -> Color(0xFF6B7280)
    TaskStateEnum.DONE       -> Color(0xFF10B981)
    TaskStateEnum.FAILED     -> Color(0xFFEF4444)
    TaskStateEnum.IDLE       -> Color(0xFF9CA3AF)
}

