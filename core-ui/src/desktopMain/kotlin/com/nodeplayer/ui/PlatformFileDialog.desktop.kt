package com.nodeplayer.ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

actual object PlatformFileDialog {
    actual fun pickFlacFiles(currentFolder: String, onFilesSelected: (List<File>) -> Unit) {
        try {
            SwingUtilities.invokeLater {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                } catch (_: Exception) {}
                val chooser = JFileChooser()
                chooser.dialogTitle = "Select Individual FLAC Files"
                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                chooser.isMultiSelectionEnabled = true
                chooser.fileFilter = FileNameExtensionFilter("FLAC Audio Files (*.flac)", "flac")
                if (currentFolder.isNotBlank()) {
                    val f = File(currentFolder)
                    if (f.exists()) chooser.currentDirectory = f
                }
                val res = chooser.showOpenDialog(null)
                if (res == JFileChooser.APPROVE_OPTION) {
                    val picked = chooser.selectedFiles.filter { it.extension.equals("flac", ignoreCase = true) && it.exists() }
                    onFilesSelected(picked)
                }
            }
        } catch (_: Exception) {}
    }
}
