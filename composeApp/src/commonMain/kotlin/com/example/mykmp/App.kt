package com.example.mykmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mykmp.di.AppModule
import com.example.mykmp.presentation.*
import org.jetbrains.compose.ui.tooling.preview.Preview

sealed class AppScreen {
    object Chat : AppScreen()
    object TaskDashboard : AppScreen()
    object Invariants : AppScreen()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Chat) }

        val chatViewModel: ChatViewModel = viewModel {
            AppModule.createChatViewModel()
        }
        val taskDashboardViewModel: TaskDashboardViewModel = viewModel {
            AppModule.createTaskDashboardViewModel()
        }
        val invariantsViewModel: InvariantsViewModel = viewModel {
            AppModule.createInvariantsViewModel()
        }

        when (screen) {
            is AppScreen.Chat -> ChatScreen(
                viewModel = chatViewModel,
                onNavigateToDashboard = { screen = AppScreen.TaskDashboard },
                onNavigateToInvariants = { screen = AppScreen.Invariants }
            )
            is AppScreen.TaskDashboard -> TaskDashboardScreen(
                viewModel = taskDashboardViewModel,
                onBack = { screen = AppScreen.Chat }
            )
            is AppScreen.Invariants -> InvariantsScreen(
                viewModel = invariantsViewModel,
                onBack = { screen = AppScreen.Chat }
            )
        }
    }
}
