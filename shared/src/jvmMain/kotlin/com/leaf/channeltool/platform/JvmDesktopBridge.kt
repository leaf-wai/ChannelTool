package com.leaf.channeltool.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FilenameFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences
import javax.swing.JFileChooser

actual fun createDesktopBridge(): DesktopBridge = JvmDesktopBridge()

internal class JvmDesktopBridge : DesktopBridge {
    private val preferences = Preferences.userNodeForPackage(JvmDesktopBridge::class.java)

    override suspend fun readDefaultPage(): String? = withContext(Dispatchers.IO) {
        preferences.get(DEFAULT_PAGE_KEY, null)
    }

    override suspend fun writeDefaultPage(page: String) = withContext(Dispatchers.IO) {
        preferences.put(DEFAULT_PAGE_KEY, page)
        preferences.flush()
    }

    override suspend fun isRegularFile(path: String): Boolean = withContext(Dispatchers.IO) {
        Files.isRegularFile(Path.of(path))
    }

    override suspend fun isDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        Files.isDirectory(Path.of(path))
    }

    override suspend fun chooseApk(): String? = showFileDialog(
        title = "选择基础 APK",
        extensions = setOf("apk"),
    )

    override suspend fun chooseChannelFile(): String? = showFileDialog(
        title = "导入渠道文件",
        extensions = setOf("txt"),
    )

    override suspend fun chooseDirectory(initialDirectory: String?): String? = withContext(Dispatchers.IO) {
        onAwtEventThread {
            if (isMacOs()) {
                synchronized(MAC_DIRECTORY_DIALOG_LOCK) {
                    val previousValue = System.getProperty(MAC_DIRECTORY_DIALOG_PROPERTY)
                    try {
                        System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, "true")
                        showNativeFileDialog(
                            title = "选择输出目录",
                            initialDirectory = initialDirectory,
                        )
                    } finally {
                        if (previousValue == null) {
                            System.clearProperty(MAC_DIRECTORY_DIALOG_PROPERTY)
                        } else {
                            System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, previousValue)
                        }
                    }
                }
            } else {
                showSwingDirectoryChooser(initialDirectory)
            }
        }
    }

    override suspend fun readUtf8Text(path: String): String = withContext(Dispatchers.IO) {
        Files.readString(Path.of(path), StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }

    override suspend fun openDirectory(path: String) = withContext(Dispatchers.IO) {
        val directory = File(path)
        require(directory.exists() && directory.isDirectory) { "输出目录尚不存在" }
        require(Desktop.isDesktopSupported()) { "当前系统不支持打开目录" }
        Desktop.getDesktop().open(directory)
    }

    override suspend fun copyText(text: String) = withContext(Dispatchers.IO) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    private suspend fun showFileDialog(
        title: String,
        extensions: Set<String>,
    ): String? = withContext(Dispatchers.IO) {
        onAwtEventThread {
            showNativeFileDialog(title = title, extensions = extensions)
        }
    }
}

private const val DEFAULT_PAGE_KEY = "defaultPage"
private const val MAC_DIRECTORY_DIALOG_PROPERTY = "apple.awt.fileDialogForDirectories"
private val MAC_DIRECTORY_DIALOG_LOCK = Any()

private fun showNativeFileDialog(
    title: String,
    initialDirectory: String? = null,
    extensions: Set<String> = emptySet(),
): String? {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
    val dialog = FileDialog(owner, title, FileDialog.LOAD)
    return try {
        dialog.directory = initialDirectory
        if (extensions.isNotEmpty()) {
            dialog.filenameFilter = FilenameFilter { _, name ->
                extensions.any { extension -> name.endsWith(".$extension", ignoreCase = true) }
            }
        }
        dialog.isMultipleMode = false
        dialog.isVisible = true
        dialog.file?.let { fileName -> File(dialog.directory, fileName).absolutePath }
    } finally {
        dialog.dispose()
    }
}

private fun showSwingDirectoryChooser(initialDirectory: String?): String? {
    val chooser = JFileChooser(initialDirectory?.let(::File)).apply {
        dialogTitle = "选择输出目录"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

private fun isMacOs(): Boolean = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

private fun <T> onAwtEventThread(block: () -> T): T {
    if (EventQueue.isDispatchThread()) return block()
    var result: Result<T>? = null
    EventQueue.invokeAndWait { result = runCatching(block) }
    return checkNotNull(result).getOrThrow()
}
