package com.leaf.channeltool.ui

import com.leaf.channeltool.model.ApkChannelInfo
import com.leaf.channeltool.model.ApkSignatureScheme
import com.leaf.channeltool.model.ChannelSource
import com.leaf.channeltool.model.PackagingItemResult
import com.leaf.channeltool.model.PackagingItemStatus
import com.leaf.channeltool.model.PackagingRequest
import com.leaf.channeltool.platform.ChannelToolService
import com.leaf.channeltool.platform.DesktopBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelToolViewModelTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun invalidChannelDisablesPackaging() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChannelToolViewModel(FakeService(), FakeDesktopBridge())

        viewModel.updateChannelText("good\nbad/name")

        assertFalse(viewModel.state.value.packaging.canStart)
        assertEquals(1, viewModel.state.value.packaging.channelIssues.size)
    }

    @Test
    fun selectedApkUsesDefaultOutputAndPackagingTracksResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChannelToolViewModel(FakeService(), FakeDesktopBridge(apk = "/tmp/base.apk"))

        viewModel.chooseBaseApk()
        advanceUntilIdle()
        viewModel.updateChannelText("one\ntwo")
        viewModel.startPackaging()
        advanceUntilIdle()

        val state = viewModel.state.value.packaging
        assertEquals("/tmp/channel-output", state.outputDirectory)
        assertEquals(2, state.results.size)
        assertTrue(state.results.all { it.status == PackagingItemStatus.Generated })
        assertFalse(state.isRunning)
    }

    @Test
    fun inspectionExposesEffectiveChannel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChannelToolViewModel(FakeService(), FakeDesktopBridge(apk = "/tmp/base.apk"))

        viewModel.chooseInspectionApk()
        advanceUntilIdle()

        assertEquals("market", viewModel.state.value.inspection.info?.effectiveChannel)
        assertEquals(ChannelSource.SigningBlock, viewModel.state.value.inspection.info?.effectiveSource)
    }

    @Test
    fun defaultPageIsRestoredAndCanBeUpdated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bridge = FakeDesktopBridge(defaultPage = AppPage.Inspection.name)
        val viewModel = ChannelToolViewModel(FakeService(), bridge)

        advanceUntilIdle()
        assertEquals(AppPage.Inspection, viewModel.state.value.page)
        assertEquals(AppPage.Inspection, viewModel.state.value.defaultPage)

        viewModel.selectPage(AppPage.Packaging)
        viewModel.setCurrentPageAsDefault()
        advanceUntilIdle()

        assertEquals(AppPage.Packaging.name, bridge.defaultPage)
        assertEquals(AppPage.Packaging, viewModel.state.value.defaultPage)
        assertTrue(viewModel.state.value.message?.text?.contains("渠道打包") == true)
    }

    @Test
    fun droppedPathsPopulatePackagingInputs() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChannelToolViewModel(FakeService(), FakeDesktopBridge())

        viewModel.useDroppedBaseApk(listOf("/tmp/base.apk"))
        advanceUntilIdle()
        assertEquals("/tmp/base.apk", viewModel.state.value.packaging.baseApkPath)
        assertEquals("/tmp/channel-output", viewModel.state.value.packaging.outputDirectory)

        viewModel.useDroppedOutputDirectory(listOf("/tmp/custom-output"))
        advanceUntilIdle()
        assertEquals("/tmp/custom-output", viewModel.state.value.packaging.outputDirectory)
    }

    @Test
    fun droppingMultipleFilesIsRejected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChannelToolViewModel(FakeService(), FakeDesktopBridge())

        viewModel.useDroppedBaseApk(listOf("/tmp/one.apk", "/tmp/two.apk"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.packaging.baseApkPath.isEmpty())
        assertTrue(viewModel.state.value.message?.text?.contains("只拖入一个") == true)
    }
}

private class FakeService : ChannelToolService {
    override suspend fun packageChannels(
        request: PackagingRequest,
        onProgress: (PackagingItemResult) -> Unit,
    ) {
        request.channels.forEach { channel ->
            onProgress(
                PackagingItemResult(
                    channel = channel,
                    targetPath = "${request.outputDirectory}/base_$channel.apk",
                    status = PackagingItemStatus.Generated,
                ),
            )
        }
    }

    override suspend fun inspectApk(apkPath: String): ApkChannelInfo = ApkChannelInfo(
        apkPath = apkPath,
        effectiveChannel = "market",
        signingBlockChannel = "market",
        v1Channel = null,
        signatures = setOf(ApkSignatureScheme.V2),
        effectiveSource = ChannelSource.SigningBlock,
    )
}

private class FakeDesktopBridge(
    private val apk: String? = null,
    var defaultPage: String? = null,
) : DesktopBridge {
    override suspend fun readDefaultPage(): String? = defaultPage
    override suspend fun writeDefaultPage(page: String) {
        defaultPage = page
    }

    override suspend fun isRegularFile(path: String): Boolean = path.endsWith(".apk", ignoreCase = true)
    override suspend fun isDirectory(path: String): Boolean = !path.endsWith(".apk", ignoreCase = true)

    override suspend fun chooseApk(): String? = apk
    override suspend fun chooseChannelFile(): String? = null
    override suspend fun chooseDirectory(initialDirectory: String?): String? = initialDirectory
    override suspend fun readUtf8Text(path: String): String = ""
    override suspend fun openDirectory(path: String) = Unit
    override suspend fun copyText(text: String) = Unit
}
