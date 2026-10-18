package com.fizr.portal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fizr.portal.service.LogEntry
import com.fizr.portal.ui.theme.*
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ServerViewModel = viewModel()) {
    val context     = LocalContext.current
    val isRunning   by vm.isRunning.collectAsState()
    val port        by vm.port.collectAsState()
    val logs        by vm.logs.collectAsState()
    val portInput   by vm.portInput.collectAsState()
    val useCustom   by vm.useCustomHtml.collectAsState()
    val customHtml  by vm.customHtmlText.collectAsState()
    val hasCustom   by vm.hasCustomFile.collectAsState()
    val snack       by vm.snackMessage.collectAsState()
    val snackState  = remember { SnackbarHostState() }

    // Permission launcher for notifications (Android 13+)
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — just best-effort */ }

    LaunchedEffect(Unit) {
        vm.init(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(snack) {
        snack?.let {
            snackState.showSnackbar(it)
            vm.snackConsumed()
        }
    }

    val localIp = remember(isRunning) { getLocalIpAddress() }

    // File picker for HTML import
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
        if (raw.isNotBlank()) vm.importHtmlFromString(context, raw)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        containerColor = SurfaceLow,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text("FIZR PORTAL", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {

            // ── STATUS CARD ───────────────────────────────────────────────
            item {
                ServerStatusCard(
                    isRunning  = isRunning,
                    port       = port,
                    localIp    = localIp,
                    portInput  = portInput,
                    onPortChange = vm::onPortChange,
                    onToggle   = {
                        if (isRunning) vm.stopServer(context) else vm.startServer(context)
                    },
                )
            }

            // ── HTML SOURCE SELECTOR ──────────────────────────────────────
            item {
                HtmlSourceCard(
                    useCustom      = useCustom,
                    hasCustom      = hasCustom,
                    customHtml     = customHtml,
                    isRunning      = isRunning,
                    onModeChange   = vm::onUseCustomChange,
                    onHtmlChange   = vm::onCustomHtmlChange,
                    onSave         = { vm.saveCustomHtml(context) },
                    onImport       = { filePicker.launch("text/*") },
                    onDelete       = { vm.deleteCustomHtml(context) },
                )
            }

            // ── ACTIVITY LOG ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Log Aktivitas", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = vm::clearLogs) {
                            Text("Bersihkan", color = OnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    EmptyLogCard()
                }
            } else {
                items(logs, key = { "${it.timestamp}-${it.uri}-${it.detail}" }) { entry ->
                    LogEntryRow(entry)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Server Status Card ────────────────────────────────────────────────────────

@Composable
private fun ServerStatusCard(
    isRunning: Boolean,
    port: Int,
    localIp: String,
    portInput: String,
    onPortChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF4CAF50) else OnSurfaceDim,
        animationSpec = tween(400),
        label = "indicator",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape  = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(indicatorColor),
                    )
                    Text(
                        text = if (isRunning) "Server Aktif" else "Server Mati",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRunning) Color.White else OnSurfaceDim,
                    )
                }

                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = RedPrimary,
                        uncheckedTrackColor = Outline,
                    ),
                )
            }

            // IP + Port info
            AnimatedVisibility(visible = isRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(label = "Alamat", value = "http://$localIp:$port")
                    InfoRow(label = "Port", value = port.toString())
                }
            }

            // Port input (only when stopped)
            AnimatedVisibility(visible = !isRunning) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = { Text("Port Server") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        focusedLabelColor  = RedPrimary,
                        cursorColor        = RedPrimary,
                        unfocusedBorderColor = Outline,
                        unfocusedLabelColor  = OnSurfaceDim,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
        Text(value, style = MaterialTheme.typography.bodySmall, color = OnSurface)
    }
}

// ── HTML Source Card ──────────────────────────────────────────────────────────

@Composable
private fun HtmlSourceCard(
    useCustom: Boolean,
    hasCustom: Boolean,
    customHtml: String,
    isRunning: Boolean,
    onModeChange: (Boolean) -> Unit,
    onHtmlChange: (String) -> Unit,
    onSave: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape  = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text("Template Halaman Login", style = MaterialTheme.typography.titleMedium, color = OnSurface)

            // Mode selector
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = !useCustom,
                        onClick  = { onModeChange(false) },
                        colors   = RadioButtonDefaults.colors(selectedColor = RedPrimary),
                    )
                    Column {
                        Text("Template Bawaan", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text("index.html bundled dari APK", style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = useCustom,
                        onClick  = { onModeChange(true) },
                        colors   = RadioButtonDefaults.colors(selectedColor = RedPrimary),
                    )
                    Column {
                        Text("HTML Kustom", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text(if (hasCustom) "File tersimpan ✓" else "Belum ada file", style = MaterialTheme.typography.labelSmall, color = if (hasCustom) Color(0xFF4CAF50) else OnSurfaceDim)
                    }
                }
            }

            // Custom HTML editor
            AnimatedVisibility(visible = useCustom) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    OutlinedTextField(
                        value        = customHtml,
                        onValueChange = onHtmlChange,
                        modifier     = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 280.dp),
                        placeholder  = { Text("Paste kode HTML di sini...", color = OnSurfaceDim, fontFamily = FontFamily.Monospace) },
                        textStyle    = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = RedPrimary,
                            unfocusedBorderColor = Outline,
                            cursorColor          = RedPrimary,
                        ),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onImport,
                            colors  = ButtonDefaults.buttonColors(containerColor = SurfaceLow),
                            shape   = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Impor File", fontSize = 13.sp)
                        }

                        Button(
                            onClick  = onSave,
                            colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape    = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simpan", fontSize = 13.sp)
                        }
                    }

                    if (hasCustom) {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus File Kustom", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Log Entry Row ─────────────────────────────────────────────────────────────

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val isLogin = entry.uri == "/login"
    val accent  = if (isLogin) RedPrimary else OnSurfaceDim

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(
                width = if (isLogin) 1.dp else 0.dp,
                color = if (isLogin) RedContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = entry.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim,
            modifier = Modifier.width(58.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.method} ${entry.uri}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface,
                fontFamily = FontFamily.Monospace,
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun EmptyLogCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.List, contentDescription = null, tint = Outline, modifier = Modifier.size(32.dp))
            Text("Belum ada aktivitas", color = OnSurfaceDim, fontSize = 13.sp)
            Text("Jalankan server lalu buka browser di perangkat lain", color = Outline, fontSize = 11.sp)
        }
    }
}

// ── Util ──────────────────────────────────────────────────────────────────────

private fun getLocalIpAddress(): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return "0.0.0.0"
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses.toList()) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: continue
                }
            }
        }
        "0.0.0.0"
    } catch (_: Exception) {
        "0.0.0.0"
    }
}
