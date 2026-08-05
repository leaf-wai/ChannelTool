package com.leaf.channeltool.platform

import com.leaf.channeltool.model.ApkChannelInfo
import com.leaf.channeltool.model.PackagingItemResult
import com.leaf.channeltool.model.PackagingRequest

interface ChannelToolService {
    suspend fun packageChannels(
        request: PackagingRequest,
        onProgress: (PackagingItemResult) -> Unit,
    )

    suspend fun inspectApk(apkPath: String): ApkChannelInfo
}

interface DesktopBridge {
    suspend fun readDefaultPage(): String?
    suspend fun writeDefaultPage(page: String)
    suspend fun isRegularFile(path: String): Boolean
    suspend fun isDirectory(path: String): Boolean
    suspend fun chooseApk(): String?
    suspend fun chooseChannelFile(): String?
    suspend fun chooseDirectory(initialDirectory: String?): String?
    suspend fun readUtf8Text(path: String): String
    suspend fun openDirectory(path: String)
    suspend fun copyText(text: String)
}

expect fun createChannelToolService(): ChannelToolService
expect fun createDesktopBridge(): DesktopBridge
