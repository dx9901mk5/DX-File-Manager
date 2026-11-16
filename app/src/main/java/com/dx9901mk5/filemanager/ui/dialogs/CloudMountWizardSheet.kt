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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lan
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import java.util.UUID

/**
 * 14 種雲端與網路連線掛載精靈
 */
@Composable
fun CloudMountWizardDialog(
    onMountCreated: (VfsMountConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val protocols = remember { VfsProtocol.entries.filter { it != VfsProtocol.LOCAL } }
    var selectedProtocol by remember { mutableStateOf<VfsProtocol?>(null) }

    var displayName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shareOrBucket by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }

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
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
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
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { /* 預留外掛擴充入口 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null)
                        Text("  下載更多 / 外掛擴充", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                val proto = selectedProtocol!!
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            label = { Text(if (proto == VfsProtocol.AMAZON_S3) "Endpoint URL" else "主機位址 / IP") },
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
                    if (proto.defaultPort > 0 && proto != VfsProtocol.AMAZON_S3) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("連接埠 (Port)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (proto.isCloud && proto != VfsProtocol.AMAZON_S3) {
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
                }
            }
        },
        confirmButton = {
            if (selectedProtocol != null) {
                Button(
                    onClick = {
                        val proto = selectedProtocol!!
                        onMountCreated(
                            VfsMountConfig(
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
                        )
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
