package com.dx9901mk5.filemanager.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.ai.AiAssistantClient
import com.dx9901mk5.filemanager.core.ai.AiMoveInstruction
import com.dx9901mk5.filemanager.core.ai.AiOrganizeResult
import com.dx9901mk5.filemanager.core.settings.AiProvider
import com.dx9901mk5.filemanager.core.settings.DxSettingsState
import com.dx9901mk5.filemanager.ui.ExplorerPaneState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiFileAssistantDialog(
    settings: DxSettingsState,
    paneState: ExplorerPaneState,
    onSelectProvider: (AiProvider) -> Unit,
    onSaveApiKey: (AiProvider, String) -> Unit,
    onApplyOrganizeMoves: suspend (List<AiMoveInstruction>) -> Result<Int>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showLoginSetup by remember(settings.aiProvider, settings.currentAiApiKey) {
        mutableStateOf(settings.currentAiApiKey.isBlank())
    }
    var keyDraft by remember(settings.aiProvider) {
        mutableStateOf(settings.aiApiKeys[settings.aiProvider].orEmpty())
    }

    var promptText by remember { mutableStateOf("請分析這個資料夾的檔案結構，並依類型建議分類整理") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<AiOrganizeResult?>(null) }
    var appliedMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (showLoginSetup) "選擇並登入 AI 助理" else "AI 檔案分析與整理 (${settings.aiProvider.displayName})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showLoginSetup) {
                    Text(
                        "第一次使用請選擇您偏好的 AI（預設為 Gemini，也可改選 GPT、Claude 或 Grok），並填入對應 API 金鑰：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AiProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = settings.aiProvider == provider,
                                onClick = {
                                    onSelectProvider(provider)
                                    keyDraft = settings.aiApiKeys[provider].orEmpty()
                                },
                                label = { Text(provider.displayName) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(settings.aiProvider.keyHelpUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("前往登入 ${settings.aiProvider.displayName} 取得金鑰")
                    }

                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text("${settings.aiProvider.displayName} API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = keyDraft.isNotBlank(),
                        onClick = {
                            onSaveApiKey(settings.aiProvider, keyDraft.trim())
                            showLoginSetup = false
                            errorMsg = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("儲存並開始分析檔案")
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "目標目錄：${paneState.currentPath}",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "共 ${paneState.files.size} 個項目（${settings.aiProvider.displayName}）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showLoginSetup = true }) {
                                Text("切換 AI")
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "依類型自動分類整理" to "請幫我把目前資料夾內的檔案依類型（圖片、影片、音訊、文件、壓縮檔、APK 等）分類整理到子資料夾。",
                            "分析檔案與空間" to "請分析這個資料夾的檔案組成與大檔案分布，給予清理建議。",
                            "找出重複或暫存檔" to "請檢查這個資料夾是否有重複檔、安裝包或可清理的暫存檔案。"
                        ).forEach { (chipLabel, chipPrompt) ->
                            AssistChip(
                                onClick = { promptText = chipPrompt },
                                label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("告訴 AI 要如何分析或整理") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !busy && promptText.isNotBlank(),
                        onClick = {
                            busy = true
                            errorMsg = null
                            appliedMsg = null
                            scope.launch {
                                val res = AiAssistantClient.analyzeAndPlan(
                                    provider = settings.aiProvider,
                                    apiKey = settings.currentAiApiKey,
                                    currentPath = paneState.currentPath,
                                    files = paneState.files,
                                    userPrompt = promptText.trim()
                                )
                                busy = false
                                res.onSuccess { aiResult = it }
                                    .onFailure { errorMsg = it.message }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("AI 分析中...")
                        } else {
                            Text("開始分析 / 產生整理計畫")
                        }
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            errorMsg = null
                            appliedMsg = null
                            aiResult = AiAssistantClient.offlineSmartOrganize(paneState.files)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("本機智慧整理（免金鑰 / 免額度）")
                    }

                    if (!errorMsg.isNullOrBlank()) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!appliedMsg.isNullOrBlank()) {
                        Text(appliedMsg!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }

                    aiResult?.let { res ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(res.summary, style = MaterialTheme.typography.bodySmall)
                                if (res.moves.isNotEmpty()) {
                                    Text(
                                        "建議移動 ${res.moves.size} 個檔案：",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    res.moves.take(8).forEach { mv ->
                                        Text(
                                            "• ${mv.fileName} → ${mv.targetFolder}/",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (res.moves.size > 8) {
                                        Text("…等共 ${res.moves.size} 項", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                val applyRes = onApplyOrganizeMoves(res.moves)
                                                busy = false
                                                applyRes.onSuccess { count ->
                                                    appliedMsg = "已完成整理，共移動 $count 個檔案！"
                                                }.onFailure { err ->
                                                    errorMsg = "套用整理失敗：${err.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("一鍵套用整理計畫 (${res.moves.size} 項)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}
