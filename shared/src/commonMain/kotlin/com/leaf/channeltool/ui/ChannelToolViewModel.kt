package com.leaf.channeltool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.channeltool.domain.defaultOutputDirectory
import com.leaf.channeltool.domain.outputFileName
import com.leaf.channeltool.domain.parseChannelText
import com.leaf.channeltool.model.ApkChannelInfo
import com.leaf.channeltool.model.ChannelValidationIssue
import com.leaf.channeltool.model.PackagingItemResult
import com.leaf.channeltool.model.PackagingItemStatus
import com.leaf.channeltool.model.PackagingRequest
import com.leaf.channeltool.platform.ChannelToolService
import com.leaf.channeltool.platform.DesktopBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppPage(val title: String) {
    Packaging("渠道打包"),
    Inspection("渠道查询"),
}

data class UserMessage(
    val id: Long,
    val text: String,
)

data class PackagingUiState(
    val baseApkPath: String = "",
    val outputDirectory: String = "",
    val channelText: String = "",
    val channels: List<String> = emptyList(),
    val channelIssues: List<ChannelValidationIssue> = emptyList(),
    val fastMode: Boolean = false,
    val overwriteExisting: Boolean = false,
    val isRunning: Boolean = false,
    val results: List<PackagingItemResult> = emptyList(),
) {
    val canStart: Boolean
        get() = !isRunning &&
            baseApkPath.isNotBlank() &&
            outputDirectory.isNotBlank() &&
            channels.isNotEmpty() &&
            channelIssues.isEmpty()

    val completedCount: Int
        get() = results.count { it.status !in setOf(PackagingItemStatus.Pending, PackagingItemStatus.Processing) }
}

data class InspectionUiState(
    val apkPath: String = "",
    val isLoading: Boolean = false,
    val info: ApkChannelInfo? = null,
    val error: String? = null,
)

data class ChannelToolUiState(
    val page: AppPage = AppPage.Packaging,
    val defaultPage: AppPage = AppPage.Packaging,
    val packaging: PackagingUiState = PackagingUiState(),
    val inspection: InspectionUiState = InspectionUiState(),
    val message: UserMessage? = null,
)

class ChannelToolViewModel(
    private val service: ChannelToolService,
    private val desktopBridge: DesktopBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelToolUiState())
    val state: StateFlow<ChannelToolUiState> = _state.asStateFlow()

    private var packagingJob: Job? = null
    private var messageId = 0L

    init {
        viewModelScope.launch {
            val savedPage = runCatching { desktopBridge.readDefaultPage() }.getOrNull()
            val defaultPage = AppPage.entries.firstOrNull { it.name == savedPage } ?: return@launch
            _state.update { it.copy(page = defaultPage, defaultPage = defaultPage) }
        }
    }

    fun selectPage(page: AppPage) {
        _state.update { it.copy(page = page) }
    }

    fun setCurrentPageAsDefault() {
        val page = _state.value.page
        viewModelScope.launch {
            bridgeAction("无法设置默认页面") {
                desktopBridge.writeDefaultPage(page.name)
            } ?: return@launch
            _state.update { it.copy(defaultPage = page) }
            showMessage("已将“${page.title}”设为默认启动页")
        }
    }

    fun updateChannelText(text: String) {
        val parsed = parseChannelText(text)
        updatePackaging {
            it.copy(channelText = text, channels = parsed.channels, channelIssues = parsed.issues)
        }
    }

    fun updateFastMode(enabled: Boolean) = updatePackaging { it.copy(fastMode = enabled) }

    fun updateOverwrite(enabled: Boolean) = updatePackaging { it.copy(overwriteExisting = enabled) }

    fun chooseBaseApk() {
        viewModelScope.launch {
            bridgeAction("无法选择 APK") { desktopBridge.chooseApk() }?.let(::setBaseApk)
        }
    }

    fun useDroppedBaseApk(paths: List<String>) {
        if (_state.value.packaging.isRunning) return
        viewModelScope.launch {
            val path = singleDroppedPath(paths, "APK 文件") ?: return@launch
            if (!path.endsWith(".apk", ignoreCase = true)) {
                showMessage("请拖入 APK 文件")
                return@launch
            }
            val isFile = bridgeAction("无法读取拖入的 APK") { desktopBridge.isRegularFile(path) } ?: return@launch
            if (!isFile) {
                showMessage("请拖入有效的 APK 文件")
                return@launch
            }
            setBaseApk(path)
            showMessage("已载入拖入的 APK")
        }
    }

    fun chooseOutputDirectory() {
        viewModelScope.launch {
            val initial = _state.value.packaging.outputDirectory.ifBlank { null }
            bridgeAction("无法选择输出目录") { desktopBridge.chooseDirectory(initial) }?.let { path ->
                updatePackaging { it.copy(outputDirectory = path) }
            }
        }
    }

    fun useDroppedOutputDirectory(paths: List<String>) {
        if (_state.value.packaging.isRunning) return
        viewModelScope.launch {
            val path = singleDroppedPath(paths, "输出目录") ?: return@launch
            val isDirectory = bridgeAction("无法读取拖入的目录") { desktopBridge.isDirectory(path) } ?: return@launch
            if (!isDirectory) {
                showMessage("请拖入文件夹作为输出目录")
                return@launch
            }
            updatePackaging { it.copy(outputDirectory = path) }
            showMessage("已设置输出目录")
        }
    }

    fun importChannelFile() {
        viewModelScope.launch {
            val path = bridgeAction("无法选择渠道文件") { desktopBridge.chooseChannelFile() } ?: return@launch
            val imported = bridgeAction("无法读取渠道文件") { desktopBridge.readUtf8Text(path) } ?: return@launch
            val current = _state.value.packaging.channelText.trimEnd()
            updateChannelText(if (current.isEmpty()) imported else "$current\n$imported")
            showMessage("已导入渠道文件")
        }
    }

    fun startPackaging() {
        val packaging = _state.value.packaging
        if (!packaging.canStart) {
            showMessage("请先补全打包信息并修正渠道错误")
            return
        }

        val request = PackagingRequest(
            baseApkPath = packaging.baseApkPath,
            outputDirectory = packaging.outputDirectory,
            channels = packaging.channels,
            fastMode = packaging.fastMode,
            overwriteExisting = packaging.overwriteExisting,
        )
        val pending = request.channels.map { channel ->
            PackagingItemResult(
                channel = channel,
                targetPath = joinPath(request.outputDirectory, outputFileName(request.baseApkPath, channel)),
                status = PackagingItemStatus.Pending,
            )
        }
        updatePackaging { it.copy(isRunning = true, results = pending) }

        packagingJob = viewModelScope.launch {
            try {
                service.packageChannels(request) { result ->
                    updatePackaging { current ->
                        current.copy(results = current.results.replaceResult(result))
                    }
                }
                val finished = _state.value.packaging.results
                val generated = finished.count { it.status == PackagingItemStatus.Generated }
                val skipped = finished.count { it.status == PackagingItemStatus.Skipped }
                val failed = finished.count { it.status == PackagingItemStatus.Failed }
                showMessage("打包完成：成功 $generated，跳过 $skipped，失败 $failed")
            } catch (_: CancellationException) {
                showMessage("已取消打包，已完成的渠道包会保留")
                throw CancellationException()
            } catch (error: Throwable) {
                showMessage(error.userFacingMessage("打包失败"))
            } finally {
                updatePackaging { it.copy(isRunning = false) }
                packagingJob = null
            }
        }
    }

    fun cancelPackaging() {
        packagingJob?.cancel()
    }

    fun openOutputDirectory() {
        val path = _state.value.packaging.outputDirectory
        if (path.isBlank()) return
        viewModelScope.launch {
            bridgeAction("无法打开输出目录") { desktopBridge.openDirectory(path) }
        }
    }

    fun chooseInspectionApk() {
        viewModelScope.launch {
            val path = bridgeAction("无法选择 APK") { desktopBridge.chooseApk() } ?: return@launch
            inspectApk(path)
        }
    }

    fun useDroppedInspectionApk(paths: List<String>) {
        if (_state.value.inspection.isLoading) return
        viewModelScope.launch {
            val path = singleDroppedPath(paths, "APK 文件") ?: return@launch
            if (!path.endsWith(".apk", ignoreCase = true)) {
                showMessage("请拖入 APK 文件")
                return@launch
            }
            val isFile = bridgeAction("无法读取拖入的 APK") { desktopBridge.isRegularFile(path) } ?: return@launch
            if (!isFile) {
                showMessage("请拖入有效的 APK 文件")
                return@launch
            }
            inspectApk(path)
        }
    }

    fun inspectAgain() {
        val path = _state.value.inspection.apkPath
        if (path.isNotBlank()) viewModelScope.launch { inspectApk(path) }
    }

    fun copyEffectiveChannel() {
        val channel = _state.value.inspection.info?.effectiveChannel ?: return
        viewModelScope.launch {
            bridgeAction("复制失败") { desktopBridge.copyText(channel) } ?: return@launch
            showMessage("渠道名已复制")
        }
    }

    fun consumeMessage(messageId: Long) {
        _state.update { current ->
            if (current.message?.id == messageId) current.copy(message = null) else current
        }
    }

    private fun setBaseApk(path: String) {
        updatePackaging {
            it.copy(
                baseApkPath = path,
                outputDirectory = defaultOutputDirectory(path),
                results = emptyList(),
            )
        }
    }

    private fun singleDroppedPath(paths: List<String>, expected: String): String? {
        if (paths.size == 1) return paths.single()
        showMessage("请只拖入一个$expected")
        return null
    }

    private suspend fun inspectApk(path: String) {
        _state.update {
            it.copy(inspection = InspectionUiState(apkPath = path, isLoading = true))
        }
        try {
            val info = service.inspectApk(path)
            _state.update {
                it.copy(inspection = InspectionUiState(apkPath = path, info = info))
            }
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    inspection = InspectionUiState(
                        apkPath = path,
                        error = error.userFacingMessage("无法读取 APK"),
                    ),
                )
            }
        }
    }

    private inline fun updatePackaging(transform: (PackagingUiState) -> PackagingUiState) {
        _state.update { it.copy(packaging = transform(it.packaging)) }
    }

    private fun showMessage(text: String) {
        _state.update { it.copy(message = UserMessage(++messageId, text)) }
    }

    private suspend fun <T> bridgeAction(fallback: String, action: suspend () -> T): T? =
        try {
            action()
        } catch (error: Throwable) {
            showMessage(error.userFacingMessage(fallback))
            null
        }
}

private fun List<PackagingItemResult>.replaceResult(result: PackagingItemResult): List<PackagingItemResult> =
    map { current -> if (current.channel == result.channel) result else current }

private fun joinPath(directory: String, fileName: String): String {
    val separator = if ('\\' in directory && '/' !in directory) "\\" else "/"
    return directory.trimEnd('/', '\\') + separator + fileName
}

private fun Throwable.userFacingMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
