package com.sdvsync.logging

import org.junit.Assert.*
import org.junit.Test

class AppLoggerTest {
    @Test
    fun credentialsAreRedactedInJsonHeadersAndDownloadUrls() {
        val input = """{"refresh_token":"shortSecret", "APIKey":"apiSecret"} Password=passwordSecret
            Authorization: Bearer headerSecret
            https://example.test/file?key=downloadSecret&expires=123
            user@example.test
        """.trimIndent()
        val output = AppLogger.sanitize(input)
        for (secret in listOf(
            "shortSecret",
            "apiSecret",
            "passwordSecret",
            "headerSecret",
            "downloadSecret",
            "user@example.test"
        )) {
            assertFalse("Leaked $secret", output.contains(secret))
        }
        assertTrue(output.contains("expires=123"))
    }
}
