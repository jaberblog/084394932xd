package com.example.screenshare

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class MjpegServer(private val port: Int) {

    @Volatile private var frame: ByteArray? = null
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    fun start() {
        serverSocket = ServerSocket(port) // روی همه اینترفیس‌ها (0.0.0.0)
        running = true
        Thread {
            while (running) {
                try {
                    val client = serverSocket!!.accept()
                    Thread { handleClient(client) }.start()
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }.start()
    }

    fun updateFrame(jpeg: ByteArray) {
        frame = jpeg
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                val out = s.getOutputStream()
                if (path.startsWith("/stream")) {
                    streamMjpeg(out)
                } else {
                    sendHtml(out)
                }
            }
        } catch (_: Exception) {
            // کلاینت قطع شده
        }
    }

    private fun sendHtml(out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Screen Share</title></head>
            <body style="margin:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;">
            <img src="/stream" style="max-width:100%;max-height:100%;" />
            </body></html>
        """.trimIndent()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" + html
        out.write(response.toByteArray())
        out.flush()
    }

    private fun streamMjpeg(out: OutputStream) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n"
        out.write(header.toByteArray())
        out.flush()

        var last: ByteArray? = null
        while (running) {
            val f = frame
            if (f != null && f !== last) {
                last = f
                val part = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n"
                out.write(part.toByteArray())
                out.write(f)
                out.write("\r\n".toByteArray())
                out.flush()
            } else {
                Thread.sleep(30)
            }
        }
    }
}