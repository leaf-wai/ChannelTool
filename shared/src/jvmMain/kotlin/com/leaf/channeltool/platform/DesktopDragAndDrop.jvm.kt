package com.leaf.channeltool.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.desktopFileDropTarget(
    enabled: Boolean,
    onFilesDropped: (List<String>) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
): Modifier {
    val currentOnFilesDropped = rememberUpdatedState(onFilesDropped)
    val currentOnDragActiveChange = rememberUpdatedState(onDragActiveChange)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                currentOnDragActiveChange.value(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnDragActiveChange.value(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnDragActiveChange.value(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnDragActiveChange.value(false)
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val paths = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    .orEmpty()
                    .filterIsInstance<File>()
                    .map(File::getAbsolutePath)
                if (paths.isEmpty()) return false
                currentOnFilesDropped.value(paths)
                return true
            }
        }
    }

    return if (enabled) {
        dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                runCatching {
                    event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                }.getOrDefault(false)
            },
            target = target,
        )
    } else {
        this
    }
}
