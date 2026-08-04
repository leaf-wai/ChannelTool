package com.leaf.channeltool.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelInputTest {
    @Test
    fun parsesBomCrLfWhitespaceAndExactDuplicates() {
        val result = parseChannelText("\uFEFF xiaomi \r\n\r\nhuawei\r\nxiaomi\r\n")

        assertEquals(listOf("xiaomi", "huawei"), result.channels)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun reportsPortableFileNameViolationsWithLineNumbers() {
        val result = parseChannelText("valid\nbad/name\nCON\ntrail.")

        assertEquals(listOf("valid"), result.channels)
        assertEquals(listOf(2, 3, 4), result.issues.map { it.line })
    }

    @Test
    fun reportsCaseInsensitiveOutputCollisions() {
        val result = parseChannelText("Store\nstore")

        assertEquals(listOf("Store"), result.channels)
        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single().message.contains("冲突"))
    }

    @Test
    fun derivesPortableOutputNamesAndDefaultDirectories() {
        assertEquals("app-release_xiaomi.apk", outputFileName("/tmp/app-release.APK", "xiaomi"))
        assertEquals("/tmp/channel-output", defaultOutputDirectory("/tmp/app-release.apk"))
        assertEquals("C:\\build\\channel-output", defaultOutputDirectory("C:\\build\\app.apk"))
    }
}
