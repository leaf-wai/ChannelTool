package com.leaf.channeltool

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ChannelTool",
        state = WindowState(width = 800.dp, height = 880.dp),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(530, 880)
        }
        App()
    }
}
