package com.leaf.channeltool.model

data class PackagingRequest(
    val baseApkPath: String,
    val outputDirectory: String,
    val channels: List<String>,
    val fastMode: Boolean,
    val overwriteExisting: Boolean,
)

enum class PackagingItemStatus {
    Pending,
    Processing,
    Generated,
    Skipped,
    Failed,
}

data class PackagingItemResult(
    val channel: String,
    val targetPath: String,
    val status: PackagingItemStatus,
    val message: String? = null,
)

enum class ApkSignatureScheme {
    V1,
    V2,
    V3,
}

enum class ChannelSource {
    SigningBlock,
    V1,
}

data class ApkChannelInfo(
    val apkPath: String,
    val effectiveChannel: String?,
    val signingBlockChannel: String?,
    val v1Channel: String?,
    val signatures: Set<ApkSignatureScheme>,
    val effectiveSource: ChannelSource?,
)

data class ChannelValidationIssue(
    val line: Int,
    val value: String,
    val message: String,
)

data class ChannelParseResult(
    val channels: List<String>,
    val issues: List<ChannelValidationIssue>,
)
