package com.dx9901mk5.filemanager.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.OAuth2AuthHelper
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import com.dx9901mk5.filemanager.core.vfs.VfsRegistry
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 14 種雲端與網路連線掛載精靈（支援瀏覽器 OAuth2 登入與連線測試）
 */
@Composable
fun CloudMountWizardDialog(
    onMountCreated: (VfsMountConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val protocols = remember { VfsProtocol.entries.filter { it != VfsProtocol.LOCAL } }
    var selectedProtocol by remember { mutableStateOf<VfsProtocol?>(null) }

    var displayName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shareOrBucket by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }

    var testing by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }

    val oauthToken by OAuth2AuthHelper.receivedToken.collectAsState()
    LaunchedEffect(oauthToken) {
        if (!oauthToken.isNullOrBlank()) {
            accessToken = oauthToken!!
            testResultMsg = "已從瀏覽器取得授權 Token，請點擊「測試連線」驗證"
            OAuth2AuthHelper.clearToken()
        }
    }

    fun buildConfig(): VfsMountConfig {
        val proto = selectedProtocol!!
        return VfsMountConfig(
            id = UUID.randomUUID().toString(),
            name = displayName.ifBlank { proto.displayName },
            protocol = proto,
            host = host.trim(),
            port = port.toIntOrNull() ?: proto.defaultPort,
            username = username.trim(),
            password = password,
            shareOrBucket = shareOrBucket.trim(),
            accessToken = accessToken.trim()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedProtocol == null) "網路連線指引 (14 種雲端 / LAN)"
                else "設定 ${selectedProtocol!!.displayName}"
            )
        },
        text = {
            if (selectedProtocol == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(protocols) { proto ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProtocol = proto
                                    displayName = proto.displayName
                                    port = if (proto.defaultPort > 0) proto.defaultPort.toString() else ""
                                    testResultMsg = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (proto.isCloud) Icons.Default.Cloud else Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = proto.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                val proto = selectedProtocol!!
                val supportsOAuthBrowser = proto in setOf(
                    VfsProtocol.GOOGLE_DRIVE,
                    VfsProtocol.DROPBOX,
                    VfsProtocol.ONEDRIVE,
                    VfsProtocol.YANDEX,
                    VfsProtocol.BOX,
                    VfsProtocol.PCLOUD
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("顯示名稱") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!proto.isCloud || proto == VfsProtocol.AMAZON_S3) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = {
                                Text(
                                    when (proto) {
                                        VfsProtocol.AMAZON_S3 -> "Endpoint URL (如 https://s3.amazonaws.com)"
                                        VfsProtocol.OWNCLOUD_NEXTCLOUD -> "伺服器網址 (如 https://cloud.example.com)"
                                        else -> "主機位址 / IP"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (proto == VfsProtocol.LAN_SMB || proto == VfsProtocol.AMAZON_S3) {
                        OutlinedTextField(
                            value = shareOrBucket,
                            onValueChange = { shareOrBucket = it },
                            label = { Text(if (proto == VfsProtocol.LAN_SMB) "共用資料夾 (Share)" else "Bucket 名稱") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (proto.defaultPort > 0 && proto != VfsProtocol.AMAZON_S3 && proto != VfsProtocol.WEBDAV && proto != VfsProtocol.OWNCLOUD_NEXTCLOUD) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("連接埠 (Port)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (proto.isCloud && proto != VfsProtocol.AMAZON_S3) {
                        if (supportsOAuthBrowser) {
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("OAuth Client ID (瀏覽器登入用)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = {
                                    OAuth2AuthHelper.launchBrowserAuth(context, proto, clientId)
                                        .onFailure { testResultMsg = it.message }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                Text("  開啟瀏覽器登入授權 (${proto.displayName})")
                            }
                            Text(
                                text = "Redirect URI: ${OAuth2AuthHelper.REDIRECT_URI}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("OAuth2 Access Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("帳號 / Access Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密碼 / Secret Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            testing = true
                            testResultMsg = "正在測試連線..."
                            val cfg = buildConfig()
                            scope.launch {
                                val reg = VfsRegistry().apply { registerMount(cfg) }
                                val res = reg.getProvider(cfg.id).listFiles(cfg.rootPath, showHidden = false)
                                testing = false
                                testResultMsg = res.fold(
                                    onSuccess = { list -> "連線成功！偵測到 ${list.size} 個項目" },
                                    onFailure = { err -> "連線失敗：${err.message}" }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (testing) "測試中..." else "測試連線")
                    }

                    testResultMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("連線成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedProtocol != null) {
                Button(
                    onClick = {
                        onMountCreated(buildConfig())
                        onDismiss()
                    }
                ) {
                    Text("掛載連線")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedProtocol != null) selectedProtocol = null else onDismiss()
            }) {
                Text(if (selectedProtocol != null) "返回" else "取消")
            }
        }
    )
}
