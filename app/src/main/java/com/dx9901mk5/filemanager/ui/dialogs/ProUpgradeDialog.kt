package com.dx9901mk5.filemanager.ui.dialogs

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.R
import com.dx9901mk5.filemanager.core.billing.ProLicenseManager
import com.dx9901mk5.filemanager.core.settings.LocalDxStrings

@Composable
fun ProUpgradeDialog(
    reasonMessage: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val s = LocalDxStrings.current
    val licenseStatus by ProLicenseManager.status.collectAsState()
    var billingNote by remember { mutableStateOf<String?>(null) }

    val (title, badgeText, featuresText, buyText, restoreText) = when (s.done) {
        "Done" -> listOf(
            "Unlock DX File Manager Pro",
            if (licenseStatus.isTrialActive) "Free Trial: ${licenseStatus.trialDaysLeft} day(s) left"
            else "7-Day Free Trial Ended",
            "• Unlimited Cloud & Network Mounts (Free: max 2 mounts, 1 account per cloud type)\n" +
                "• Multi-Account Support for Same Cloud Provider\n" +
                "• Freeform Multi-Window Mode (up to 8 resizable floating windows with cross-app drag & drop, as shown below)",
            if (licenseStatus.productPriceLabel != null) "Unlock Pro (${licenseStatus.productPriceLabel})" else "Unlock Pro",
            "Restore Purchase"
        )
        "完了" -> listOf(
            "DX ファイルマネージャー Pro を解除",
            if (licenseStatus.isTrialActive) "無料体験：残り ${licenseStatus.trialDaysLeft} 日"
            else "7日間の無料体験が終了しました",
            "・クラウド／ネットワーク接続の上限解除（無料版は最大2個・同種クラウド1アカウントまで）\n" +
                "・同一クラウドの複数アカウント同時接続に対応\n" +
                "・マルチウィンドウ（フリーフォーム小窓）モード解放（下図のように最大8個の小窓でドラッグ＆ドロップ連携）",
            if (licenseStatus.productPriceLabel != null) "Pro版を購入 (${licenseStatus.productPriceLabel})" else "Pro版を購入",
            "購入を復元"
        )
        "完成" -> if (s.selectAll == "全选") listOf(
            "升级 DX 文件管理器 Pro 版",
            if (licenseStatus.isTrialActive) "免费全功能试用：剩余 ${licenseStatus.trialDaysLeft} 天"
            else "7 天免费试用已结束",
            "• 解锁云盘／网络挂载数量上限（免费版最多 2 个，且同类别仅限 1 个账号）\n" +
                "• 支持同一类别云盘挂载多个账号\n" +
                "• 解锁自由小窗模式（如下图所示，最多同时开启 8 个小窗并支持跨 App 拖拽交互）",
            if (licenseStatus.productPriceLabel != null) "立即解锁 (${licenseStatus.productPriceLabel})" else "立即解锁 Pro 版",
            "恢复购买"
        ) else listOf(
            "升級 DX檔案管理器 Pro 版",
            if (licenseStatus.isTrialActive) "免費全功能試用：剩餘 ${licenseStatus.trialDaysLeft} 天"
            else "7 天免費試用已結束",
            "• 解除雲端／網路硬碟掛載上限（免費版最多 2 個，且單一類別僅限 1 個帳號）\n" +
                "• 支援單一類別雲端同時掛載多個帳號\n" +
                "• 解鎖自由小視窗模式（如下圖所示，最多同時開啟 8 個獨立分區小視窗並跨 App 拖曳交互）",
            if (licenseStatus.productPriceLabel != null) "立即解鎖 (${licenseStatus.productPriceLabel})" else "立即解鎖 Pro 版",
            "恢復購買"
        )
        else -> listOf(
            "升級 DX檔案管理器 Pro 版",
            if (licenseStatus.isTrialActive) "免費全功能試用：剩餘 ${licenseStatus.trialDaysLeft} 天"
            else "7 天免費試用已結束",
            "• 解除雲端／網路硬碟掛載上限（免費版最多 2 個，且單一類別僅限 1 個帳號）\n" +
                "• 支援單一類別雲端同時掛載多個帳號\n" +
                "• 解鎖自由小視窗模式（如下圖所示，最多同時開啟 8 個獨立分區小視窗並跨 App 拖曳交互）",
            if (licenseStatus.productPriceLabel != null) "立即解鎖 (${licenseStatus.productPriceLabel})" else "立即解鎖 Pro 版",
            "恢復購買"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (!reasonMessage.isNullOrBlank()) {
                    Text(
                        text = reasonMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = featuresText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 圖 2：雙開自由小視窗功能補充展示圖
                Image(
                    painter = painterResource(id = R.drawable.pro_multiwindow_preview),
                    contentDescription = "自由小視窗功能展示",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                billingNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activity == null) return@Button
                    ProLicenseManager.launchPurchase(activity) { msg ->
                        billingNote = msg
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ProLicenseManager.status.value.isProPurchased) {
                            onDismiss()
                        }
                    }
                }
            ) {
                Text(buyText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        ProLicenseManager.restorePurchases(context) { msg ->
                            billingNote = msg
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ProLicenseManager.status.value.isProPurchased) {
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text(restoreText)
                }
                TextButton(onClick = onDismiss) {
                    Text(s.cancel)
                }
            }
        }
    )
}
