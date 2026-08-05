package com.leaf.channeltool.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Modifier.desktopFileDropTarget(
    enabled: Boolean,
    onFilesDropped: (List<String>) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
): Modifier
