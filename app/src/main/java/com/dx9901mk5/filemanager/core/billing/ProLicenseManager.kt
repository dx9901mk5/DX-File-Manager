package com.dx9901mk5.filemanager.core.billing

import android.app.Activity
import android.content.Context
import android.os.Build
import com.dx9901mk5.filemanager.BuildConfig
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

data class ProLicenseStatus(
    val isProPurchased: Boolean = false,
    val isTrialActive: Boolean = true,
    val trialDaysLeft: Int = 7,
    val hasProAccess: Boolean = true,
    val isMultiWindowLocked: Boolean = false,
    val productPriceLabel: String? = null
)

object ProLicenseManager {
    const val PRO_PRODUCT_ID = "dx_pro_unlock"
    private const val PREF_NAME = "dx_pro_license_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch_ms"
    private const val KEY_PRO_PURCHASED = "pro_purchased"
    private const val TRIAL_DURATION_DAYS = 7L
    private val TRIAL_DURATION_MS = TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS)

    private val _status = MutableStateFlow(ProLicenseStatus())
    val status: StateFlow<ProLicenseStatus> = _status.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var savedFirst = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
        val pkgInstallTime = runCatching {
            val pm = appCtx.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(appCtx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appCtx.packageName, 0)
            }
            info.firstInstallTime
        }.getOrDefault(0L)

        val effectiveFirst = when {
            savedFirst > 0L && pkgInstallTime > 0L -> minOf(savedFirst, pkgInstallTime)
            savedFirst > 0L -> savedFirst
            pkgInstallTime > 0L -> pkgInstallTime
            else -> now
        }
        if (savedFirst != effectiveFirst) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH, effectiveFirst).apply()
        }
        refreshStatus(appCtx)
        PlayBillingBridge.init(appCtx)
    }

    fun refreshStatus(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val purchased = prefs.getBoolean(KEY_PRO_PURCHASED, false)
        if (!BuildConfig.IS_PLAY_EDITION) {
            _status.value = ProLicenseStatus(
                isProPurchased = true,
                isTrialActive = true,
                trialDaysLeft = 7,
                hasProAccess = true,
                isMultiWindowLocked = false,
                productPriceLabel = _status.value.productPriceLabel
            )
            return
        }
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
        val elapsed = (System.currentTimeMillis() - firstLaunch).coerceAtLeast(0L)
        val remainingMs = (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
        val trialActive = remainingMs > 0L
        val daysLeft = if (trialActive) {
            ceil(remainingMs.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble()).toInt().coerceIn(1, 7)
        } else {
            0
        }
        val hasAccess = purchased || trialActive
        _status.value = ProLicenseStatus(
            isProPurchased = purchased,
            isTrialActive = trialActive,
            trialDaysLeft = daysLeft,
            hasProAccess = hasAccess,
            isMultiWindowLocked = !hasAccess,
            productPriceLabel = _status.value.productPriceLabel
        )
    }

    fun setProPurchased(context: Context, purchased: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO_PURCHASED, purchased)
            .apply()
        refreshStatus(context)
    }

    fun updateProductPriceLabel(price: String?) {
        _status.value = _status.value.copy(productPriceLabel = price)
    }

    fun canAddCloudMount(
        context: Context,
        existingMounts: List<VfsMountConfig>,
        targetProtocol: VfsProtocol
    ): Result<Unit> {
        refreshStatus(context)
        if (_status.value.hasProAccess) return Result.success(Unit)
        if (existingMounts.size >= 2) {
            return Result.failure(
                IllegalStateException("7 天免費試用已結束：免費版最多只能掛載 2 個雲端／網路硬碟，升級 Pro 版即可解除數量上限。")
            )
        }
        if (existingMounts.any { it.protocol == targetProtocol }) {
            return Result.failure(
                IllegalStateException("7 天免費試用已結束：免費版單一類別雲端僅限掛載 1 個帳號（最多兩類各 1 個），升級 Pro 版即可同類別掛載多個帳號。")
            )
        }
        return Result.success(Unit)
    }

    fun launchPurchase(activity: Activity, onMessage: (String) -> Unit) {
        PlayBillingBridge.launchPurchase(activity, onMessage)
    }

    fun restorePurchases(context: Context, onMessage: (String) -> Unit) {
        PlayBillingBridge.restorePurchases(context, onMessage)
    }
}
