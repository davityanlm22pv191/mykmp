package com.example.mykmp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mykmp.domain.profile.*

/**
 * Панель управления профилями пользователя.
 */
@Composable
fun ProfilePanel(
    profiles: List<UserProfile>,
    activeProfileId: String?,
    onCreateProfile: (displayName: String) -> Unit,
    onSwitchProfile: (profileId: String) -> Unit,
    onUpdateResponseStyle: (profileId: String, style: String) -> Unit,
    onUpdateTone: (profileId: String, tone: String) -> Unit,
    onUpdateExpertise: (profileId: String, level: String) -> Unit,
    onAddLanguage: (profileId: String, language: String) -> Unit,
    onRemoveLanguage: (profileId: String, language: String) -> Unit,
    onAddArchitecture: (profileId: String, arch: String) -> Unit,
    onRemoveArchitecture: (profileId: String, arch: String) -> Unit,
    onAddBudgetLimit: (profileId: String, key: String, value: String) -> Unit,
    onRemoveBudgetLimit: (profileId: String, key: String) -> Unit,
    onAddTimeConstraint: (profileId: String, key: String, value: String) -> Unit,
    onRemoveTimeConstraint: (profileId: String, key: String) -> Unit,
    onDeleteProfile: (profileId: String) -> Unit
) {
    val activeProfile = profiles.find { it.id == activeProfileId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === Выбор профиля ===
            ProfileSelectorRow(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSwitchProfile = onSwitchProfile,
                onCreateProfile = onCreateProfile
            )

            if (activeProfile != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // === Редактор активного профиля ===
                ProfileEditor(
                    profile = activeProfile,
                    onUpdateResponseStyle = { onUpdateResponseStyle(activeProfile.id, it) },
                    onUpdateTone = { onUpdateTone(activeProfile.id, it) },
                    onUpdateExpertise = { onUpdateExpertise(activeProfile.id, it) },
                    onAddLanguage = { onAddLanguage(activeProfile.id, it) },
                    onRemoveLanguage = { onRemoveLanguage(activeProfile.id, it) },
                    onAddArchitecture = { onAddArchitecture(activeProfile.id, it) },
                    onRemoveArchitecture = { onRemoveArchitecture(activeProfile.id, it) },
                    onAddBudgetLimit = { k, v -> onAddBudgetLimit(activeProfile.id, k, v) },
                    onRemoveBudgetLimit = { onRemoveBudgetLimit(activeProfile.id, it) },
                    onAddTimeConstraint = { k, v -> onAddTimeConstraint(activeProfile.id, k, v) },
                    onRemoveTimeConstraint = { onRemoveTimeConstraint(activeProfile.id, it) },
                    onDeleteProfile = { onDeleteProfile(activeProfile.id) }
                )
            }
        }
    }
}

// === Выбор / создание профиля ===

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSelectorRow(
    profiles: List<UserProfile>,
    activeProfileId: String?,
    onSwitchProfile: (profileId: String) -> Unit,
    onCreateProfile: (displayName: String) -> Unit
) {
    Text(
        text = "Профиль",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        profiles.forEach { profile ->
            FilterChip(
                selected = profile.id == activeProfileId,
                onClick = { onSwitchProfile(profile.id) },
                label = { Text(profile.displayName, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    var newName by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Новый профиль", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                if (newName.isNotBlank()) {
                    onCreateProfile(newName.trim())
                    newName = ""
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// === Редактор профиля ===

@Composable
private fun ProfileEditor(
    profile: UserProfile,
    onUpdateResponseStyle: (String) -> Unit,
    onUpdateTone: (String) -> Unit,
    onUpdateExpertise: (String) -> Unit,
    onAddLanguage: (String) -> Unit,
    onRemoveLanguage: (String) -> Unit,
    onAddArchitecture: (String) -> Unit,
    onRemoveArchitecture: (String) -> Unit,
    onAddBudgetLimit: (key: String, value: String) -> Unit,
    onRemoveBudgetLimit: (key: String) -> Unit,
    onAddTimeConstraint: (key: String, value: String) -> Unit,
    onRemoveTimeConstraint: (key: String) -> Unit,
    onDeleteProfile: () -> Unit
) {
    // Языки программирования
    ChipsEditor(
        label = "Языки",
        items = profile.preferredLanguages,
        onAdd = onAddLanguage,
        onRemove = onRemoveLanguage,
        placeholder = "Kotlin, Swift..."
    )

    // Архитектурные паттерны
    ChipsEditor(
        label = "Архитектура",
        items = profile.architecturePrefs,
        onAdd = onAddArchitecture,
        onRemove = onRemoveArchitecture,
        placeholder = "MVVM, Clean Arch..."
    )

    // Стиль ответов
    EnumChipGroup(
        label = "Ответы",
        options = ResponseStyle.entries.map { it.name to it.displayName() },
        selected = profile.responseStyle,
        onSelect = onUpdateResponseStyle
    )

    // Тон
    EnumChipGroup(
        label = "Тон",
        options = Tone.entries.map { it.name to it.displayName() },
        selected = profile.tone,
        onSelect = onUpdateTone
    )

    // Уровень
    EnumChipGroup(
        label = "Уровень",
        options = ExpertiseLevel.entries.map { it.name to it.displayName() },
        selected = profile.expertiseLevel,
        onSelect = onUpdateExpertise
    )

    // Бюджетные ограничения
    KeyValueEditor(
        label = "Бюджет",
        items = profile.budgetLimits,
        onAdd = onAddBudgetLimit,
        onRemove = onRemoveBudgetLimit,
        keyPlaceholder = "Сервис",
        valuePlaceholder = "50$/мес"
    )

    // Временные ограничения
    KeyValueEditor(
        label = "Сроки",
        items = profile.timeConstraints,
        onAdd = onAddTimeConstraint,
        onRemove = onRemoveTimeConstraint,
        keyPlaceholder = "Этап",
        valuePlaceholder = "2 нед"
    )

    // Удаление профиля
    TextButton(
        onClick = onDeleteProfile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Удалить профиль",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// === Вспомогательные компоненты ===

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsEditor(
    label: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    placeholder: String
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            InputChip(
                selected = false,
                onClick = { onRemove(item) },
                label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Text("\u00D7", style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }

    var newItem by remember(label) { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                if (newItem.isNotBlank()) {
                    onAdd(newItem.trim())
                    newItem = ""
                }
            },
            modifier = Modifier.height(36.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumChipGroup(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (name, display) ->
                FilterChip(
                    selected = selected == name,
                    onClick = { onSelect(name) },
                    label = { Text(display, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun KeyValueEditor(
    label: String,
    items: Map<String, String>,
    onAdd: (key: String, value: String) -> Unit,
    onRemove: (key: String) -> Unit,
    keyPlaceholder: String,
    valuePlaceholder: String
) {
    if (items.isNotEmpty()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$key: $value",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemove(key) }, modifier = Modifier.size(24.dp)) {
                        Text("\u00D7", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    var key by remember(label) { mutableStateOf("") }
    var value by remember(label) { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(keyPlaceholder, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1.5f),
            placeholder = { Text(valuePlaceholder, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                if (key.isNotBlank() && value.isNotBlank()) {
                    onAdd(key.trim(), value.trim())
                    key = ""
                    value = ""
                }
            },
            modifier = Modifier.height(36.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * Баннер предложений по обновлению профиля (из авто-экстракции).
 */
@Composable
fun ProfileSuggestionBanner(
    suggestion: ProfileSuggestion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Обновить профиль?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            val detected = buildList {
                addAll(suggestion.preferredLanguages)
                addAll(suggestion.architecturePrefs)
                suggestion.budgetLimits.forEach { (k, v) -> add("$k: $v") }
                suggestion.timeConstraints.forEach { (k, v) -> add("$k: $v") }
            }

            Text(
                "Обнаружено: ${detected.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Пропустить", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onConfirm) {
                    Text(
                        "Добавить",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
