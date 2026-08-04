package com.leaf.channeltool.platform

import com.leaf.channeltool.domain.outputFileName
import com.leaf.channeltool.model.ApkChannelInfo
import com.leaf.channeltool.model.ApkSignatureScheme
import com.leaf.channeltool.model.ChannelSource
import com.leaf.channeltool.model.PackagingItemResult
import com.leaf.channeltool.model.PackagingItemStatus
import com.leaf.channeltool.model.PackagingRequest
import com.tencent.vasdolly.common.ApkSectionInfo
import com.tencent.vasdolly.reader.ChannelReader
import com.tencent.vasdolly.writer.ChannelWriter
import com.tencent.vasdolly.writer.IdValueWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

actual fun createChannelToolService(): ChannelToolService = JvmChannelToolService()

internal class JvmChannelToolService : ChannelToolService {
    override suspend fun packageChannels(
        request: PackagingRequest,
        onProgress: (PackagingItemResult) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val baseApk = request.baseApkPath.toValidatedApk()
        val outputDirectory = Path.of(request.outputDirectory).toAbsolutePath().normalize()
        Files.createDirectories(outputDirectory)
        require(Files.isDirectory(outputDirectory)) { "输出路径不是目录" }

        val signature = detectWritableSignature(baseApk)
        val apkSectionInfo = if (signature == WritableSignature.SigningBlock) {
            IdValueWriter.getApkSectionInfo(baseApk, false)
        } else {
            null
        }

        request.channels.forEach { channel ->
            coroutineContext.ensureActive()
            val target = outputDirectory.resolve(outputFileName(baseApk.name, channel))
            val baseResult = PackagingItemResult(channel, target.toString(), PackagingItemStatus.Pending)

            if (Files.exists(target) && !request.overwriteExisting) {
                onProgress(baseResult.copy(status = PackagingItemStatus.Skipped, message = "目标文件已存在"))
                return@forEach
            }

            onProgress(baseResult.copy(status = PackagingItemStatus.Processing))
            var temporaryFile: Path? = null
            try {
                temporaryFile = Files.createTempFile(outputDirectory, ".channeltool-", ".apk")
                writeChannel(signature, apkSectionInfo, baseApk, temporaryFile.toFile(), channel)
                coroutineContext.ensureActive()

                if (!request.fastMode) {
                    val verified = when (signature) {
                        WritableSignature.SigningBlock -> ChannelReader.verifyChannelByV2(temporaryFile.toFile(), channel)
                        WritableSignature.V1 -> ChannelReader.verifyChannelByV1(temporaryFile.toFile(), channel)
                    }
                    check(verified) { "渠道校验失败" }
                }

                moveIntoPlace(temporaryFile, target, request.overwriteExisting)
                temporaryFile = null
                onProgress(baseResult.copy(status = PackagingItemStatus.Generated))
            } catch (error: kotlinx.coroutines.CancellationException) {
                temporaryFile?.let(Files::deleteIfExists)
                throw error
            } catch (error: Throwable) {
                temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
                onProgress(
                    baseResult.copy(
                        status = PackagingItemStatus.Failed,
                        message = error.message?.takeIf(String::isNotBlank) ?: "生成失败",
                    ),
                )
            }
        }
    }

    override suspend fun inspectApk(apkPath: String): ApkChannelInfo = withContext(Dispatchers.IO) {
        val apk = apkPath.toValidatedApk()
        val signatures = buildSet {
            if (ChannelReader.containV1Signature(apk)) add(ApkSignatureScheme.V1)
            if (ChannelReader.containV2Signature(apk)) add(ApkSignatureScheme.V2)
            if (ChannelReader.containV3Signature(apk)) add(ApkSignatureScheme.V3)
        }
        val hasSigningBlock = ApkSignatureScheme.V2 in signatures || ApkSignatureScheme.V3 in signatures
        val signingBlockChannel = if (hasSigningBlock) ChannelReader.getChannelByV2(apk) else null
        val v1Channel = if (ApkSignatureScheme.V1 in signatures) ChannelReader.getChannelByV1(apk) else null
        val effectiveChannel = signingBlockChannel ?: v1Channel

        ApkChannelInfo(
            apkPath = apk.absolutePath,
            effectiveChannel = effectiveChannel,
            signingBlockChannel = signingBlockChannel,
            v1Channel = v1Channel,
            signatures = signatures,
            effectiveSource = when {
                signingBlockChannel != null -> ChannelSource.SigningBlock
                v1Channel != null -> ChannelSource.V1
                else -> null
            },
        )
    }

    private fun detectWritableSignature(apk: File): WritableSignature {
        if (ChannelReader.containV2Signature(apk) || ChannelReader.containV3Signature(apk)) {
            return WritableSignature.SigningBlock
        }
        if (ChannelReader.containV1Signature(apk)) return WritableSignature.V1
        error("APK 未检测到 VasDolly 支持的 V1、V2 或 V3 签名")
    }

    private fun writeChannel(
        signature: WritableSignature,
        apkSectionInfo: ApkSectionInfo?,
        baseApk: File,
        destination: File,
        channel: String,
    ) {
        when (signature) {
            WritableSignature.SigningBlock -> ChannelWriter.addChannelByV2(
                checkNotNull(apkSectionInfo),
                destination,
                channel,
            )

            WritableSignature.V1 -> ChannelWriter.addChannelByV1(baseApk, destination, channel)
        }
    }

    private fun moveIntoPlace(source: Path, target: Path, overwrite: Boolean) {
        val options = buildList {
            add(StandardCopyOption.ATOMIC_MOVE)
            if (overwrite) add(StandardCopyOption.REPLACE_EXISTING)
        }.toTypedArray()
        try {
            Files.move(source, target, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (overwrite) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
        }
    }
}

private enum class WritableSignature {
    SigningBlock,
    V1,
}

private fun String.toValidatedApk(): File {
    val file = File(this).absoluteFile
    require(file.exists() && file.isFile) { "APK 文件不存在" }
    require(file.extension.equals("apk", ignoreCase = true)) { "请选择 .apk 文件" }
    return file
}
