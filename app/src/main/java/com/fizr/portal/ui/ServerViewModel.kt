package com.fizr.portal.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fizr.portal.manager.HtmlManager
import com.fizr.portal.service.LogEntry
import com.fizr.portal.service.ServerState
import com.fizr.portal.service.WebServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel : ViewModel() {

    // ── Mirror service-level state ────────────────────────────────────────
    val isRunning: StateFlow<Boolean> = ServerState.isRunning
    val port: StateFlow<Int>          = ServerState.port
    val logs: StateFlow<List<LogEntry>> = ServerState.logs

    // ── Local UI state ────────────────────────────────────────────────────
    private val _portInput      = MutableStateFlow("8080")
    val portInput: StateFlow<String> = _portInput.asStateFlow()

    private val _useCustomHtml  = MutableStateFlow(false)
    val useCustomHtml: StateFlow<Boolean> = _useCustomHtml.asStateFlow()

    private val _customHtmlText = MutableStateFlow("")
    val customHtmlText: StateFlow<String> = _customHtmlText.asStateFlow()

    private val _hasCustomFile  = MutableStateFlow(false)
    val hasCustomFile: StateFlow<Boolean> = _hasCustomFile.asStateFlow()

    private val _snackMessage   = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────

    fun init(context: Context) {
        val htmlManager = HtmlManager(context)
        _hasCustomFile.value = htmlManager.hasCustomHtml()
        if (htmlManager.hasCustomHtml()) {
            _customHtmlText.value = htmlManager.readCustomHtml()
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────

    fun onPortChange(v: String) {
        if (v.length <= 5 && v.all { it.isDigit() }) _portInput.value = v
    }

    fun onUseCustomChange(v: Boolean) {
        _useCustomHtml.value = v
    }

    fun onCustomHtmlChange(v: String) {
        _customHtmlText.value = v
    }

    fun saveCustomHtml(context: Context) {
        val html = _customHtmlText.value.trim()
        if (html.isBlank()) {
            _snackMessage.value = "HTML kosong, tidak disimpan."
            return
        }
        HtmlManager(context).saveCustomHtml(html)
        _hasCustomFile.value = true
        _snackMessage.value = "HTML kustom berhasil disimpan."
    }

    fun importHtmlFromString(context: Context, raw: String) {
        _customHtmlText.value = raw
        HtmlManager(context).saveCustomHtml(raw)
        _hasCustomFile.value = true
        _snackMessage.value = "File HTML berhasil diimpor."
    }

    fun deleteCustomHtml(context: Context) {
        HtmlManager(context).deleteCustomHtml()
        _customHtmlText.value = ""
        _hasCustomFile.value  = false
        _useCustomHtml.value  = false
        _snackMessage.value   = "HTML kustom dihapus."
    }

    fun startServer(context: Context) {
        val resolvedPort = _portInput.value.toIntOrNull()?.coerceIn(1, 65535) ?: 8080
        _portInput.value = resolvedPort.toString()
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_START
            putExtra(WebServerService.EXTRA_PORT, resolvedPort)
            putExtra(WebServerService.EXTRA_CUSTOM, _useCustomHtml.value)
        }
        context.startForegroundService(intent)
    }

    fun stopServer(context: Context) {
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun clearLogs() {
        ServerState.clearLogs()
    }

    fun snackConsumed() {
        _snackMessage.value = null
    }
}
