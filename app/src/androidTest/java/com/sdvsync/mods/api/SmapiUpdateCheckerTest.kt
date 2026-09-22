package com.sdvsync.mods.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sdvsync.mods.ModManifestParser
import com.sdvsync.mods.models.InstalledMod
import com.sdvsync.mods.models.ModManifest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmapiUpdateCheckerTest {
    private val mod = InstalledMod(ModManifest("Test", "Author", "1.0", "Test.Mod", ""), "Test", "/fixture", true)
    private fun checker(code: Int, body: String) = SmapiUpdateChecker(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(body.toResponseBody()).build()
        }.build()
    )

    @Test
    fun failedUpdateRequestIsNotAnEmptySuccessfulCheck() = runBlocking {
        try {
            checker(500, "[]").checkForUpdates(listOf(mod))
            fail("HTTP failure was hidden")
        } catch (_: IllegalStateException) { }
    }

    @Test
    fun updateIdsMatchInstalledCase() = runBlocking {
        val updates = checker(
            200,
            """[{"id":"test.mod","suggestedUpdate":{"version":"2.0"}}]"""
        ).checkForUpdates(listOf(mod))
        assertEquals(setOf("Test.Mod"), updates.keys)
    }

    @Test
    fun manifestCleanupPreservesPunctuationInsideStrings() {
        val manifest = ModManifestParser().parse(
            """{"Name":"Test","Version":"1.0","UniqueID":"Test.Mod","Description":"Keep ,} and ,] exactly",}"""
        )
        assertEquals("Keep ,} and ,] exactly", manifest?.description)
    }
}
