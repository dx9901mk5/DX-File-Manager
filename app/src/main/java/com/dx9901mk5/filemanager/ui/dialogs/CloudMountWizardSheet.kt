package com.dx9901mk5.filemanager.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.dx9901mk5.filemanager.core.vfs.CloudAndWebDavVfsProvider
import com.dx9901mk5.filemanager.core.vfs.FtpVfsProvider
import com.dx9901mk5.filemanager.core.vfs.SftpVfsProvider
import com.dx9901mk5.filemanager.core.vfs.SmbVfsProvider
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID

/**
 * 14 種雲端與網路連線掛載精靈（支援 OAuth2 網頁登入授權 + 掛載前即時連線測試）
 */
@Composable
fun CloudMountWizardDialog(
    receivedOAuthToken: String? = null,
    onConsumeOAuthToken: () -> Unit = {},
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
    var testStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(receivedOAuthToken) {
        if (!receivedOAuthToken.isNullOrBlank()) {
            accessToken = receivedOAuthToken
            testStatus = "已接收 OAuth2 授權 Token，請點擊「測試並掛載」"
            onConsumeOAuthToken()
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

    fun buildOAuthUrl(proto: VfsProtocol, cid: String): String {
        val redirect = URLEncoder.encode("com.dx9901mk5.filemanager://oauth2redirect", "UTF-8")
        val encodedCid = URLEncoder.encode(cid.trim(), "UTF-8")
        return when (proto) {
            VfsProtocol.GOOGLE_DRIVE ->
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=$encodedCid&redirect_uri=$redirect&response_type=token&scope=${URLEncoder.encode("https://www.googleapis.com/auth/drive", "UTF-8")}"
            VfsProtocol.ONEDRIVE ->
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=$encodedCid&redirect_uri=$redirect&response_type=token&scope=${URLEncoder.encode("Files.ReadWrite.All", "UTF-8")}"
            VfsProtocol.DROPBOX ->
                "https://www.dropbox.com/oauth2/authorize?client_id=$encodedCid&redirect_uri=$redirect&response_type=token"
            VfsProtocol.BOX ->
                "https://account.box.com/api/oauth2/authorize?client_id=$encodedCid&redirect_uri=$redirect&response_type=code"
            VfsProtocol.PCLOUD ->
                "https://my.pcloud.com/oauth2/authorize?client_id=$encodedCid&redirect_uri=$redirect&response_type=token"
            VfsProtocol.YANDEX ->
                "https://oauth.yandex.com/authorize?client_id=$encodedCid&redirect_uri=$redirect&response_type=token"
            else -> ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedProtocol == null) "網路連線指引 (14 種雲端 / LAN)"
                else "連線設定：${selectedProtocol!!.displayName}"
            )
        },
        text = {
            if (selectedProtocol == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
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
                                    testStatus = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (proto.isCloud) Icons.Default.Cloud else Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = proto.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                val proto = selectedProtocol!!
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
                        val oauthUrlSupported = proto in setOf(
                            VfsProtocol.GOOGLE_DRIVE,
                            VfsProtocol.ONEDRIVE,
                            VfsProtocol.DROPBOX,
                            VfsProtocol.BOX,
                            VfsProtocol.PCLOUD,
                            VfsProtocol.YANDEX
                        )
                        if (oauthUrlSupported) {
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("OAuth2 Client ID (網頁登入用)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                enabled = clientId.isNotBlank(),
                                onClick = {
                                    val url = buildOAuthUrl(proto, clientId)
                                    if (url.isNotEmpty()) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("前往網頁登入授權")
                            }
                        }
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Access Token (授權後自動填入或手動貼上)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(if (proto == VfsProtocol.AMAZON_S3) "Access Key" else "帳號") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (proto == VfsProtocol.AMAZON_S3) "Secret Key" else "密碼") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    testStatus?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedProtocol != null) {
                Button(
                    enabled = !testing,
                    onClick = {
                        val cfg = buildConfig()
                        testing = true
                        testStatus = "正在驗證連線與帳號權限..."
                        scope.launch {
                            val provider = when (cfg.protocol) {
                                VfsProtocol.LAN_SMB -> SmbVfsProvider(cfg)
                                VfsProtocol.FTP -> FtpVfsProvider(cfg)
                                VfsProtocol.SFTP -> SftpVfsProvider(cfg)
                                else -> CloudAndWebDavVfsProvider(cfg)
                            }
                            val res = provider.listFiles("/", showHidden = false)
                            testing = false
                            res.onSuccess { list ->
                                testStatus = "✅ 連線成功（共 ${list.size} 個項目）"
                                onMountCreated(cfg)
                                onDismiss()
                            }.onFailure { err ->
                                testStatus = "連線失敗：${err.message ?: "請檢查帳號、Token 或網路設定"}"
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (testing) "測試中..." else "測試並掛載")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedProtocol != null) {
                    selectedProtocol = null
                    testStatus = null
                } else {
                    onDismiss()
                }
            }) {
                Text(if (selectedProtocol != null) "返回" else "取消")
            }
        }
    )
}
