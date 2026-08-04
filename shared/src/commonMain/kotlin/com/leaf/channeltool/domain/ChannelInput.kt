package com.leaf.channeltool.domain

import com.leaf.channeltool.model.ChannelParseResult
import com.leaf.channeltool.model.ChannelValidationIssue

private val forbiddenFileNameCharacters = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
private val windowsReservedNames = buildSet {
    addAll(listOf("CON", "PRN", "AUX", "NUL"))
    addAll((1..9).map { "COM$it" })
    addAll((1..9).map { "LPT$it" })
}

fun parseChannelText(text: String): ChannelParseResult {
    val channels = mutableListOf<String>()
    val issues = mutableListOf<ChannelValidationIssue>()
    val outputNames = mutableSetOf<String>()

    text.removePrefix("\uFEFF").lines().forEachIndexed { index, rawLine ->
        val channel = rawLine.trim()
        if (channel.isEmpty()) return@forEachIndexed

        val issue = validateChannelName(channel)
        if (issue != null) {
            issues += ChannelValidationIssue(index + 1, channel, issue)
            return@forEachIndexed
        }

        val collisionKey = channel.lowercase()
        if (!outputNames.add(collisionKey)) {
            if (channels.none { it == channel }) {
                issues += ChannelValidationIssue(
                    line = index + 1,
                    value = channel,
                    message = "与已有渠道生成的文件名冲突",
                )
            }
            return@forEachIndexed
        }
        channels += channel
    }

    return ChannelParseResult(channels = channels, issues = issues)
}

fun validateChannelName(channel: String): String? {
    if (channel.any { it.code < 32 || it == '\u007F' }) return "不能包含控制字符"
    if (channel.any { it in forbiddenFileNameCharacters }) return "不能包含 < > : \" / \\ | ? *"
    if (channel.endsWith('.') || channel.endsWith(' ')) return "不能以点或空格结尾"
    val stem = channel.substringBefore('.').uppercase()
    if (stem in windowsReservedNames) return "不能使用 Windows 保留名称"
    return null
}

fun outputFileName(baseApkPath: String, channel: String): String {
    val fileName = baseApkPath.substringAfterLast('/').substringAfterLast('\\')
    val baseName = fileName.removeSuffixIgnoreCase(".apk")
    return "${baseName}_$channel.apk"
}

fun defaultOutputDirectory(baseApkPath: String): String {
    val separatorIndex = maxOf(baseApkPath.lastIndexOf('/'), baseApkPath.lastIndexOf('\\'))
    if (separatorIndex < 0) return "channel-output"
    return baseApkPath.substring(0, separatorIndex + 1) + "channel-output"
}

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
