package com.example.mykmp

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Chat",
        state = rememberWindowState(width = 700.dp, height = 900.dp)
    ) {
        App()
    }
}
