package com.sdvsync.download

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class DownloadTransportTest {
    @Test
    fun rejectsCleartextBeforeOpeningAConnection() = runBlocking {
        val root = Files.createTempDirectory("download-transport").toFile()
        try {
            try {
                downloadToFile(OkHttpClient(), "http://127.0.0.1:1/file.apk", root.resolve("file.apk"))
                fail("Cleartext download accepted")
            } catch (_: IllegalArgumentException) { }
            assertTrue(root.list()!!.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsHttpsDowngradeAndPreservesExistingFile() = serverTest { server, client, target ->
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://127.0.0.1:1/file.apk").build())
        try {
            downloadToFile(client, server.url("/file.apk").toString(), target)
            fail("HTTPS downgrade accepted")
        } catch (e: IllegalStateException) {
            assertEquals("Download failed: HTTP 302", e.message)
        }
        assertEquals("original", target.readText())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellingAStalledResponseClosesTheSocketPromptly() = serverTest { server, client, target ->
        server.enqueue(MockResponse.Builder().body("replacement").bodyDelay(30, TimeUnit.SECONDS).build())
        kotlinx.coroutines.coroutineScope {
            val download = launch(Dispatchers.IO) {
                downloadToFile(client, server.url("/file.apk").toString(), target)
            }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            download.cancel()
            withTimeout(3_000) { download.join() }
            assertTrue(download.isCancelled)
        }
        assertEquals("original", target.readText())
        assertEquals(listOf("file.apk"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun secureDownloadPublishesAllBytes() = serverTest { server, client, target ->
        server.enqueue(MockResponse.Builder().body("replacement").build())
        downloadToFile(client, server.url("/file.apk").toString(), target)
        assertEquals("replacement", target.readText())
    }

    private fun serverTest(action: suspend (MockWebServer, OkHttpClient, java.io.File) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("download-transport").toFile()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .build()
        try {
            MockWebServer().use { server ->
                server.useHttps(serverTls.sslSocketFactory())
                server.start()
                action(server, client, root.resolve("file.apk").apply { writeText("original") })
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            root.deleteRecursively()
        }
    }
}
