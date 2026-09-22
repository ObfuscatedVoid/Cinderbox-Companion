package com.sdvsync

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NxmDownloadParamsTest {
    @Test
    fun validLinkPreservesTheEncodedDownloadKey() {
        val params = parseNxmUrl(Uri.parse("nxm://stardewvalley/mods/12/files/34?key=a%2Bb&expires=123456"))
        assertEquals(NxmDownloadParams("12", "34", "a+b", "123456"), params)
    }

    @Test
    fun wrongGamesMalformedIdsAndMissingKeysAreRejected() {
        for (link in listOf(
            "nxm://othergame/mods/12/files/34?key=a&expires=123",
            "nxm://stardewvalley/mods/../files/34?key=a&expires=123",
            "nxm://stardewvalley/mods/12/files/34/extra?key=a&expires=123",
            "nxm://stardewvalley/mods/12/files/34?key=&expires=123",
            "nxm://stardewvalley/mods/12/files/34?key=a&expires=invalid"
        )) {
            assertNull(parseNxmUrl(Uri.parse(link)))
        }
    }
}
