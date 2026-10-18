package com.nungil.cloud

import com.nungil.contract.Box
import com.nungil.contract.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** CloudLook against a tiny local HTTP server: the answer comes back, a bad key fails, no network is "offline", a slow server fails. */
class CloudLookTest {
    private lateinit var server: ServerSocket
    private val lastKey = AtomicReference<String?>()
    private val lastPath = AtomicReference<String?>()
    @Volatile private var status = 200
    @Volatile private var body = """{"answer":"A door ahead.","qr":null,"ms":321}"""
    @Volatile private var sleepMs = 0L
    private val jpeg = ByteArray(64) { it.toByte() }

    @Before fun start() {
        server = ServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: Exception) { return@thread }
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.use { s ->
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
        val request = reader.readLine() ?: return@use
        lastPath.set(request.split(" ").getOrNull(1))
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val (name, value) = line.split(":", limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
            if (name == "x-nungil-key") lastKey.set(value)
            if (name == "content-length") length = value.toInt()
        }
        val buffer = CharArray(length)
        var read = 0
        while (read < length) {
            val n = reader.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        if (sleepMs > 0) Thread.sleep(sleepMs)
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\ncontent-type: application/json\r\ncontent-length: ${bytes.size}\r\nconnection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
        }
    }

    @After fun stop() = server.close()

    private fun url() = "http://127.0.0.1:${server.localPort}"

    @Test fun okAnswer() {
        val r = CloudLook(url(), "k1").ask(jpeg, "q", Lang.EN, Box(0f, 0f, 1f, 1f))
        assertTrue(r.toString(), r is CloudLook.Result.Ok)
        assertEquals("A door ahead.", (r as CloudLook.Result.Ok).answer.text)
        assertEquals("k1", lastKey.get())
        assertEquals("/look", lastPath.get())
    }

    @Test fun badKeyIsFailed() {
        status = 403
        body = """{"error":"bad key"}"""
        val r = CloudLook(url(), "wrong").ask(jpeg, "q", Lang.EN, null)
        assertTrue(r.toString(), r is CloudLook.Result.Failed)
    }

    @Test fun serverErrorIsFailed() {
        status = 502
        body = """{"error":"the model did not answer"}"""
        assertTrue(CloudLook(url(), "k").ask(jpeg, "q", Lang.KO, null) is CloudLook.Result.Failed)
    }

    @Test fun unknownHostIsOffline() {
        val t = System.nanoTime()
        val r = CloudLook("http://nungil.invalid", "k", connectTimeoutMs = 1500).ask(jpeg, "q", Lang.EN, null)
        assertTrue(r.toString(), r is CloudLook.Result.Offline)
        assertTrue((System.nanoTime() - t) / 1e6 < 5000)
    }

    @Test fun slowServerIsFailed() {
        sleepMs = 2000
        val r = CloudLook(url(), "k", readTimeoutMs = 500).ask(jpeg, "q", Lang.EN, null)
        assertTrue(r.toString(), r is CloudLook.Result.Failed)
    }

    @Test fun trailingSlashInTheUrlIsFine() {
        val r = CloudLook(url() + "/", "k").ask(jpeg, "q", Lang.EN, null)
        assertTrue(r.toString(), r is CloudLook.Result.Ok)
        assertEquals("/look", lastPath.get())
    }
}
