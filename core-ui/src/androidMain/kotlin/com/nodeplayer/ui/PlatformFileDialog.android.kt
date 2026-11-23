package com.nodeplayer.ui

import java.io.File

actual object PlatformFileDialog {
    actual fun pickFlacFiles(currentFolder: String, onFilesSelected: (List<File>) -> Unit) {
        // Handled on Android by activity result launchers
    }
}
