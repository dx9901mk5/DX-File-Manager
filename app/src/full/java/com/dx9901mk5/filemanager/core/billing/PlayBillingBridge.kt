package com.dx9901mk5.filemanager.core.billing

import android.app.Activity
import android.content.Context

object PlayBillingBridge {
    fun init(context: Context) {
        // GitHub 版無須串接 Google Play Billing
    }

    fun launchPurchase(activity: Activity, onMessage: (String) -> Unit) {
        onMessage("GitHub 版無須付費解鎖")
    }

    fun restorePurchases(context: Context, onMessage: (String) -> Unit) {
        onMessage("GitHub 版已啟用完整功能")
    }
}
