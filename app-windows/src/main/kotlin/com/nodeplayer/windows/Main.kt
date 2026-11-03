package com.nodeplayer.windows

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nodeplayer.windows.audio.WindowsPlaybackEngine
import com.nodeplayer.windows.data.DesktopSettingsStore
import com.nodeplayer.windows.data.DesktopTrackDatabase
import com.nodeplayer.windows.ui.MainWindow

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1100.dp, 720.dp),
        position = WindowPosition.PlatformDefault
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Node Player",
        state = windowState
    ) {
        val engine = remember { WindowsPlaybackEngine() }
        val database = remember { DesktopTrackDatabase() }
        val settingsStore = remember { DesktopSettingsStore() }

        DisposableEffect(Unit) {
            onDispose {
                engine.release()
            }
        }

        MainWindow(
            engine = engine,
            database = database,
            settingsStore = settingsStore
        )
    }
}
