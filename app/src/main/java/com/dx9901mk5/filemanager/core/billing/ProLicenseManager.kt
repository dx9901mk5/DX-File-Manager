package com.dx9901mk5.filemanager.core.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.dx9901mk5.filemanager.BuildConfig
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

enum class ProPlan(
    val productId: String,
    val deviceQuota: Int,
    val isUnlimited: Boolean,
    val defaultPriceLabel: String
) {
    DEVICES_3("dx_pro_3devices", 3, false, "NT$ 200"),
    DEVICES_10("dx_pro_10devices", 10, false, "NT$ 500"),
    UNLIMITED("dx_pro_unlimited", Int.MAX_VALUE, true, "NT$ 1,200")
}

data class ProLicenseStatus(
    val isProPurchased: Boolean = false,
    val isUnlimited: Boolean = false,
    val maxDevices: Int = 0,
    val boundDeviceCount: Int = 0,
    val currentDeviceBound: Boolean = false,
    val deviceLimitReached: Boolean = false,
    val isTrialActive: Boolean = true,
    val trialDaysLeft: Int = 7,
    val hasProAccess: Boolean = true,
    val isMultiWindowLocked: Boolean = false,
    val planPrices: Map<ProPlan, String> = emptyMap()
)

object ProLicenseManager {
    private const val PREF_NAME = "dx_pro_license_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch_ms"
    private const val KEY_IS_UNLIMITED = "pro_unlimited"
    private const val KEY_MAX_DEVICES = "pro_max_devices"
    private const val KEY_BOUND_DEVICES = "pro_bound_devices"
    private const val TRIAL_DURATION_DAYS = 7L
    private val TRIAL_DURATION_MS = TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS)

    private val _status = MutableStateFlow(ProLicenseStatus())
    val status: StateFlow<ProLicenseStatus> = _status.asStateFlow()

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        if (androidId.isNotBlank()) return androidId
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}"
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val savedFirst = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
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
        if (!BuildConfig.IS_PLAY_EDITION) {
            _status.value = ProLicenseStatus(
                isProPurchased = true,
                isUnlimited = true,
                maxDevices = Int.MAX_VALUE,
                boundDeviceCount = 1,
                currentDeviceBound = true,
                deviceLimitReached = false,
                isTrialActive = true,
                trialDaysLeft = 7,
                hasProAccess = true,
                isMultiWindowLocked = false,
                planPrices = _status.value.planPrices
            )
            return
        }

        val unlimited = prefs.getBoolean(KEY_IS_UNLIMITED, false)
        val maxDevices = prefs.getInt(KEY_MAX_DEVICES, 0)
        val boundSet = (prefs.getStringSet(KEY_BOUND_DEVICES, emptySet()) ?: emptySet()).toMutableSet()
        val currentId = getDeviceId(appCtx)

        // 若有裝置額度且尚未達上限，且本機尚未綁定，則自動綁定（原機重裝/G帳號還原時 currentId 已在 boundSet 中，不會重複累計）
        var currentBound = currentId in boundSet
        if (!unlimited && maxDevices > 0 && !currentBound && boundSet.size < maxDevices) {
            boundSet.add(currentId)
            prefs.edit().putStringSet(KEY_BOUND_DEVICES, boundSet).apply()
            currentBound = true
        }

        val hasPaidAccess = unlimited || (maxDevices > 0 && currentBound)
        val limitReached = !unlimited && maxDevices > 0 && !currentBound && boundSet.size >= maxDevices

        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
        val elapsed = (System.currentTimeMillis() - firstLaunch).coerceAtLeast(0L)
        val remainingMs = (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
        val trialActive = remainingMs > 0L
        val daysLeft = if (trialActive) {
            ceil(remainingMs.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble()).toInt().coerceIn(1, 7)
        } else {
            0
        }
        val hasAccess = hasPaidAccess || trialActive
        _status.value = ProLicenseStatus(
            isProPurchased = hasPaidAccess,
            isUnlimited = unlimited,
            maxDevices = maxDevices,
            boundDeviceCount = boundSet.size,
            currentDeviceBound = currentBound,
            deviceLimitReached = limitReached,
            isTrialActive = trialActive,
            trialDaysLeft = daysLeft,
            hasProAccess = hasAccess,
            isMultiWindowLocked = !hasAccess,
            planPrices = _status.value.planPrices
        )
    }

    fun grantPlanPurchase(context: Context, plan: ProPlan) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (plan.isUnlimited) {
            editor.putBoolean(KEY_IS_UNLIMITED, true)
        } else {
            val prevMax = prefs.getInt(KEY_MAX_DEVICES, 0)
            val boundSet = (prefs.getStringSet(KEY_BOUND_DEVICES, emptySet()) ?: emptySet()).toMutableSet()
            val currentId = getDeviceId(appCtx)
            // 僅在尚未綁定本機時才增加綁定紀錄；原手機重裝或 G 帳號還原不會重複累計數量
            val newMax = prevMax + plan.deviceQuota
            boundSet.add(currentId)
            editor.putInt(KEY_MAX_DEVICES, newMax)
            editor.putStringSet(KEY_BOUND_DEVICES, boundSet)
        }
        editor.apply()
        refreshStatus(appCtx)
    }

    fun updatePlanPrices(prices: Map<ProPlan, String>) {
        _status.value = _status.value.copy(planPrices = prices)
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
                IllegalStateException("7 天免費試用已結束：免費版最多只能掛載 2 個雲端／網路硬碟，升級授權即可解除數量上限。")
            )
        }
        if (existingMounts.any { it.protocol == targetProtocol }) {
            return Result.failure(
                IllegalStateException("7 天免費試用已結束：免費版單一類別雲端僅限掛載 1 個帳號（最多兩類各 1 個），升級授權即可同類別掛載多個帳號。")
            )
        }
        return Result.success(Unit)
    }

    fun launchPurchase(activity: Activity, plan: ProPlan, onMessage: (String) -> Unit) {
        PlayBillingBridge.launchPurchase(activity, plan, onMessage)
    }

    fun restorePurchases(context: Context, onMessage: (String) -> Unit) {
        PlayBillingBridge.restorePurchases(context, onMessage)
    }
}
