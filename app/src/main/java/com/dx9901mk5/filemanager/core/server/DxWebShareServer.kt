package com.dx9901mk5.filemanager.core.server

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * DX 區域網路網頁分享伺服器 (純原創輕量 HTTP/1.1 Socket 實作)
 * 風格採用 DX 檔案管理器黑底金黃配色，供區網電腦或手機瀏覽器直接瀏覽與下載檔案。
 */
object DxWebShareServer {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isRunning: Boolean = false
        private set

    var currentPort: Int = 8899
        private set

    var rootDir: File = Environment.getExternalStorageDirectory()
        private set

    fun start(shareRoot: File = Environment.getExternalStorageDirectory(), port: Int = 8899): Result<String> {
        if (isRunning) return Result.success(getAccessUrl())
        return runCatching {
            rootDir = shareRoot
            currentPort = port
            val socket = ServerSocket(port)
            serverSocket = socket
            isRunning = true

            serverJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(client) }
                    } catch (_: Exception) {
                        if (socket.isClosed) break
                    }
                }
            }
            getAccessUrl()
        }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    fun getAccessUrl(): String {
        val ip = getLocalIpv4Address() ?: "127.0.0.1"
        return "http://$ip:$currentPort/"
    }

    private fun getLocalIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        }.getOrNull()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val out = s.getOutputStream()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val rawPath = parts[1].substringBefore("?")
            val decodedRel = URLDecoder.decode(rawPath, "UTF-8").trimStart('/')
            val target = File(rootDir, decodedRel).canonicalFile

            // 防止目錄穿越攻擊
            if (!target.path.startsWith(rootDir.canonicalPath)) {
                sendText(out, "403 Forbidden", "Access Denied")
                return
            }

            if (!target.exists()) {
                sendText(out, "404 Not Found", "File Not Found")
                return
            }

            if (target.isDirectory) {
                sendDirectoryHtml(out, target, decodedRel)
            } else {
                sendFile(out, target)
            }
        }
    }

    private fun sendDirectoryHtml(out: OutputStream, dir: File, relPath: String) {
        val children = dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })

        val rows = buildString {
            if (relPath.isNotEmpty()) {
                val parentRel = relPath.substringBeforeLast('/', "")
                val parentHref = if (parentRel.isEmpty()) "/" else "/$parentRel"
                append("<tr><td><a href=\"$parentHref\">📁 .. (上一層)</a></td><td>-</td></tr>")
            }
            for (f in children) {
                val childRel = if (relPath.isEmpty()) f.name else "$relPath/${f.name}"
                val encodedHref = "/" + childRel.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val icon = if (f.isDirectory) "📁" else "📄"
                val sizeText = if (f.isDirectory) "資料夾" else "${f.length()} Bytes"
                append("<tr><td><a href=\"$encodedHref\">$icon ${escapeHtml(f.name)}</a></td><td>$sizeText</td></tr>")
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="zh-Hant">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>DX檔案管理器 - 網頁分享</title>
              <style>
                body { background:#0B0B0B; color:#F2F2F2; font-family:Segoe UI,sans-serif; margin:20px; }
                h2 { color:#FFC83D; border-bottom:1px solid #333; padding-bottom:8px; }
                table { width:100%; border-collapse:collapse; margin-top:12px; }
                th, td { text-align:left; padding:10px; border-bottom:1px solid #242424; }
                th { color:#FFC83D; }
                a { color:#FFE48F; text-decoration:none; }
                a:hover { text-decoration:underline; color:#FFC83D; }
              </style>
            </head>
            <body>
              <h2>DX檔案管理器 — 區域網路分享 (/${escapeHtml(relPath)})</h2>
              <table>
                <tr><th>名稱</th><th>大小</th></tr>
                $rows
              </table>
            </body>
            </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun sendFile(out: OutputStream, file: File) {
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Disposition: attachment; filename*=UTF-8''$encodedName\r\n" +
            "Content-Length: ${file.length()}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        file.inputStream().use { input ->
            input.copyTo(out, bufferSize = 64 * 1024)
        }
        out.flush()
    }

    private fun sendText(out: OutputStream, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
