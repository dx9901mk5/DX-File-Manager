package com.dx9901mk5.filemanager.ui.dialogs

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.GoogleTokenResult
import com.dx9901mk5.filemanager.core.vfs.OAuth2AuthHelper
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import com.dx9901mk5.filemanager.core.vfs.VfsRegistry
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 5 步驟網路連線指引（比照 Solid Explorer 精靈流程，支援同平台多重帳號掛載）
 * Step 1: 連線類型
 * Step 2: 顯示名稱
 * Step 3: 登入（系統帳戶選擇器 / OAuth2 / 伺服器帳密）
 * Step 4: 總覽
 * Step 5: 就快完成了！→ 連線驗證並掛載
 */
@Composable
fun CloudMountWizardDialog(
    onMountCreated: (VfsMountConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val protocols = remember {
        listOf(
            VfsProtocol.GOOGLE_DRIVE,
            VfsProtocol.ONEDRIVE,
            VfsProtocol.LAN_SMB,
            VfsProtocol.FTP,
            VfsProtocol.SFTP,
            VfsProtocol.WEBDAV,
            VfsProtocol.OWNCLOUD_NEXTCLOUD,
            VfsProtocol.AMAZON_S3
        )
    }

    var step by remember { mutableIntStateOf(1) }
    var selectedProtocol by remember { mutableStateOf(VfsProtocol.GOOGLE_DRIVE) }

    var displayName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shareOrBucket by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }

    var loggedInAccount by remember { mutableStateOf<String?>(null) }
    var showAdvancedAuth by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var pendingGoogleAccount by remember { mutableStateOf<String?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val acct = pendingGoogleAccount
        if (result.resultCode == Activity.RESULT_OK && !acct.isNullOrBlank()) {
            authBusy = true
            scope.launch {
                when (val res = OAuth2AuthHelper.fetchGoogleDriveToken(context, acct)) {
                    is GoogleTokenResult.Success -> {
                        accessToken = res.token
                        loggedInAccount = res.accountName
                        username = res.accountName
                        statusMessage = null
                    }
                    is GoogleTokenResult.Error -> {
                        statusMessage = res.message
                        showAdvancedAuth = true
                    }
                    is GoogleTokenResult.Recoverable -> {
                        statusMessage = "請再試一次完成 Google 授權"
                    }
                }
                authBusy = false
            }
        } else {
            authBusy = false
            statusMessage = "已取消 Google 授權"
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                pendingGoogleAccount = accountName
                authBusy = true
                statusMessage = null
                scope.launch {
                    when (val res = OAuth2AuthHelper.fetchGoogleDriveToken(context, accountName)) {
                        is GoogleTokenResult.Success -> {
                            accessToken = res.token
                            loggedInAccount = res.accountName
                            username = res.accountName
                            authBusy = false
                        }
                        is GoogleTokenResult.Recoverable -> {
                            consentLauncher.launch(res.intent)
                        }
                        is GoogleTokenResult.Error -> {
                            loggedInAccount = null
                            username = accountName
                            authBusy = false
                            statusMessage = res.message
                            showAdvancedAuth = true
                        }
                    }
                }
            }
        }
    }

    val oauthToken by OAuth2AuthHelper.receivedToken.collectAsState()
    LaunchedEffect(oauthToken) {
        if (!oauthToken.isNullOrBlank()) {
            accessToken = oauthToken!!
            if (loggedInAccount.isNullOrBlank()) {
                loggedInAccount = username.ifBlank { displayName.ifBlank { selectedProtocol.displayName } }
            }
            statusMessage = null
            OAuth2AuthHelper.clearToken()
        }
    }

    fun buildConfig(): VfsMountConfig {
        return VfsMountConfig(
            id = UUID.randomUUID().toString(),
            name = displayName.trim().ifBlank { selectedProtocol.displayName },
            protocol = selectedProtocol,
            host = host.trim(),
            port = port.toIntOrNull() ?: selectedProtocol.defaultPort,
            username = (loggedInAccount ?: username).trim(),
            password = password,
            shareOrBucket = shareOrBucket.trim(),
            accessToken = accessToken.trim()
        )
    }

    val isOAuthCloud = selectedProtocol == VfsProtocol.GOOGLE_DRIVE || selectedProtocol == VfsProtocol.ONEDRIVE

    val stepTitle = when (step) {
        1 -> "網路連線指引 — 連線類型"
        2 -> "網路連線指引 — 顯示名稱"
        3 -> "網路連線指引 — 登入"
        4 -> "網路連線指引 — 總覽"
        else -> "就快完成了！"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stepTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                LinearProgressIndicator(
                    progress = { step / 5f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            when (step) {
                // Step 1: 連線類型（清單式單選，比照示範影片 00:04）
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(protocols) { proto ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProtocol = proto
                                        port = if (proto.defaultPort > 0) proto.defaultPort.toString() else ""
                                        statusMessage = null
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (proto.isCloud) Icons.Default.Cloud else Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = proto.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                RadioButton(
                                    selected = selectedProtocol == proto,
                                    onClick = {
                                        selectedProtocol = proto
                                        port = if (proto.defaultPort > 0) proto.defaultPort.toString() else ""
                                        statusMessage = null
                                    }
                                )
                            }
                        }
                    }
                }

                // Step 2: 顯示名稱（支援多重帳號命名，比照示範影片 00:06-00:09）
                2 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "用於顯示此連線的名稱（可建立多個不同帳號）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("顯示名稱") },
                            placeholder = { Text(selectedProtocol.displayName) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Step 3: 登入（依平台自動切換系統 Google 帳號選擇器 / 雲端登入 / 伺服器帳密）
                3 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isOAuthCloud) {
                            if (loggedInAccount != null && accessToken.isNotBlank()) {
                                Text(
                                    text = "你已經以「$loggedInAccount」的身份登入",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = {
                                        loggedInAccount = null
                                        accessToken = ""
                                        username = ""
                                        statusMessage = null
                                    }
                                ) {
                                    Text("登出")
                                }
                            } else if (authBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Text("請稍候...", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Button(
                                    onClick = {
                                        statusMessage = null
                                        if (selectedProtocol == VfsProtocol.GOOGLE_DRIVE) {
                                            runCatching {
                                                accountPickerLauncher.launch(OAuth2AuthHelper.createGoogleAccountPickerIntent())
                                            }.onFailure {
                                                statusMessage = "無法開啟系統 Google 帳戶選擇器：${it.message}"
                                                showAdvancedAuth = true
                                            }
                                        } else {
                                            OAuth2AuthHelper.launchBrowserAuth(context, selectedProtocol, clientId)
                                                .onFailure {
                                                    statusMessage = it.message
                                                    showAdvancedAuth = true
                                                }
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("登入到 ${selectedProtocol.displayName}")
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAdvancedAuth = !showAdvancedAuth }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "進階：手動 Token / Client ID",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (showAdvancedAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = showAdvancedAuth) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = clientId,
                                        onValueChange = { clientId = it },
                                        label = { Text("OAuth Client ID（瀏覽器登入用）") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (selectedProtocol == VfsProtocol.GOOGLE_DRIVE && clientId.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                statusMessage = null
                                                OAuth2AuthHelper.launchBrowserAuth(context, selectedProtocol, clientId)
                                                    .onFailure { statusMessage = it.message }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("改用瀏覽器 OAuth 登入 Google 雲端硬碟")
                                        }
                                    }
                                    OutlinedTextField(
                                        value = accessToken,
                                        onValueChange = {
                                            accessToken = it
                                            if (it.isNotBlank() && loggedInAccount == null) {
                                                loggedInAccount = displayName.ifBlank { selectedProtocol.displayName }
                                            }
                                        },
                                        label = { Text("OAuth2 Access Token") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // 自建協定：LAN/SMB, FTP, SFTP, WebDAV, OwnCloud/Nextcloud, Amazon S3
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = {
                                    Text(
                                        when (selectedProtocol) {
                                            VfsProtocol.WEBDAV, VfsProtocol.OWNCLOUD_NEXTCLOUD -> "伺服器網址 (https://...)"
                                            VfsProtocol.AMAZON_S3 -> "Endpoint (如 s3.amazonaws.com)"
                                            else -> "主機位址 / IP"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (selectedProtocol.defaultPort > 0) {
                                OutlinedTextField(
                                    value = port,
                                    onValueChange = { port = it.filter(Char::isDigit) },
                                    label = { Text("連接埠 (Port)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (selectedProtocol == VfsProtocol.LAN_SMB || selectedProtocol == VfsProtocol.AMAZON_S3) {
                                OutlinedTextField(
                                    value = shareOrBucket,
                                    onValueChange = { shareOrBucket = it },
                                    label = { Text(if (selectedProtocol == VfsProtocol.LAN_SMB) "共用資料夾名稱 (Share)" else "Bucket 名稱") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(if (selectedProtocol == VfsProtocol.AMAZON_S3) "Access Key" else "使用者名稱 / 帳號") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(if (selectedProtocol == VfsProtocol.AMAZON_S3) "Secret Key" else "密碼") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Step 4: 總覽（比照示範影片 00:14）
                4 -> {
                    val effectiveName = displayName.trim().ifBlank { selectedProtocol.displayName }
                    val effectiveUser = (loggedInAccount ?: username).ifBlank { host.ifBlank { "未設定" } }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryRow("連線類型", selectedProtocol.displayName)
                            SummaryRow("顯示名稱", effectiveName)
                            SummaryRow("使用者名稱", effectiveUser)
                        }
                    }
                }

                // Step 5: 就快完成了！（比照示範影片 00:15-00:17）
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            enabled = !connecting,
                            onClick = {
                                connecting = true
                                statusMessage = null
                                val cfg = buildConfig()
                                scope.launch {
                                    val tempRegistry = VfsRegistry().apply { registerMount(cfg) }
                                    val res = tempRegistry.getProvider(cfg.id).listFiles(cfg.rootPath, false)
                                    connecting = false
                                    res.onSuccess {
                                        onMountCreated(cfg)
                                        onDismiss()
                                    }.onFailure { err ->
                                        statusMessage = "連線失敗：${err.message}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                        ) {
                            if (connecting) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text("正在連線...", style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                Text("連線", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step < 5) {
                Button(
                    onClick = {
                        if (step == 2 && displayName.isBlank()) {
                            displayName = selectedProtocol.displayName
                        }
                        if (step == 3 && isOAuthCloud && accessToken.isBlank()) {
                            statusMessage = "尚未取得授權 Token，請先完成登入或展開進階設定貼上 Access Token"
                            showAdvancedAuth = true
                            return@Button
                        }
                        statusMessage = null
                        step++
                    }
                ) {
                    Text("下一步")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                if (step > 1) {
                    TextButton(
                        enabled = !connecting,
                        onClick = { step-- }
                    ) {
                        Text("上一步")
                    }
                }
            }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}
