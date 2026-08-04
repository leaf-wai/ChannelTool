package com.leaf.channeltool.platform

import com.leaf.channeltool.model.ApkSignatureScheme
import com.leaf.channeltool.model.PackagingItemStatus
import com.leaf.channeltool.model.PackagingRequest
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmChannelToolServiceTest {
    private val service = JvmChannelToolService()

    @Test
    fun packagesAndReadsV1Channel() = runTest {
        val directory = createTempDirectory("channeltool-v1-")
        val source = copyFixture("fixtures/v1-signed.apk", directory.resolve("base-v1.apk"))
        val results = mutableListOf<com.leaf.channeltool.model.PackagingItemResult>()

        service.packageChannels(
            request = PackagingRequest(
                baseApkPath = source.toString(),
                outputDirectory = directory.resolve("output").toString(),
                channels = listOf("market"),
                fastMode = false,
                overwriteExisting = false,
            ),
            onProgress = results::add,
        )

        val output = directory.resolve("output/base-v1_market.apk")
        assertTrue(Files.exists(output))
        assertEquals(PackagingItemStatus.Generated, results.last().status)
        val info = service.inspectApk(output.toString())
        assertEquals("market", info.effectiveChannel)
        assertTrue(ApkSignatureScheme.V1 in info.signatures)
    }

    @Test
    fun packagesAndReadsV2ChannelThenSkipsExistingOutput() = runTest {
        val directory = createTempDirectory("channeltool-v2-")
        val source = copyFixture("fixtures/v2-signed.apk", directory.resolve("base-v2.apk"))
        val request = PackagingRequest(
            baseApkPath = source.toString(),
            outputDirectory = directory.resolve("output").toString(),
            channels = listOf("xiaomi"),
            fastMode = false,
            overwriteExisting = false,
        )

        service.packageChannels(request) {}
        val secondRun = mutableListOf<com.leaf.channeltool.model.PackagingItemResult>()
        service.packageChannels(request, secondRun::add)

        val output = directory.resolve("output/base-v2_xiaomi.apk")
        val info = service.inspectApk(output.toString())
        assertEquals("xiaomi", info.signingBlockChannel)
        assertTrue(ApkSignatureScheme.V2 in info.signatures)
        assertEquals(PackagingItemStatus.Skipped, secondRun.single().status)
    }

    @Test
    fun rejectsCorruptApkForPackaging() = runTest {
        val directory = createTempDirectory("channeltool-corrupt-")
        val corrupt = directory.resolve("corrupt.apk")
        Files.writeString(corrupt, "not an apk")

        assertFailsWith<Throwable> {
            service.packageChannels(
                PackagingRequest(
                    baseApkPath = corrupt.toString(),
                    outputDirectory = directory.resolve("output").toString(),
                    channels = listOf("market"),
                    fastMode = false,
                    overwriteExisting = false,
                ),
            ) {}
        }
    }

    private fun copyFixture(resource: String, destination: Path): Path {
        val input = checkNotNull(javaClass.classLoader.getResourceAsStream(resource))
        input.use { Files.copy(it, destination) }
        return destination
    }
}
