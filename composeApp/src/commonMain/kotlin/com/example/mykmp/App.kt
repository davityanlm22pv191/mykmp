package com.example.mykmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mykmp.di.AppModule
import com.example.mykmp.presentation.ChatScreen
import com.example.mykmp.presentation.ChatViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val chatViewModel: ChatViewModel = viewModel {
            AppModule.createChatViewModel()
        }
        ChatScreen(viewModel = chatViewModel)
    }
}
