package com.nodeplayer.ui

import java.io.File

expect object PlatformFileDialog {
    fun pickFlacFiles(currentFolder: String, onFilesSelected: (List<File>) -> Unit)
}
