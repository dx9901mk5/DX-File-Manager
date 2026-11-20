package com.dx9901mk5.filemanager.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

object PlayBillingBridge : PurchasesUpdatedListener {
    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var pendingCallback: ((String) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (billingClient != null) return
        val client = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        billingClient = client
        connectAndQuery()
    }

    private fun connectAndQuery(onReady: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            onReady?.invoke()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAllProductDetails()
                    queryOwnedPurchases(null)
                    onReady?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                // 下次操作時自動重連
            }
        })
    }

    private fun queryAllProductDetails() {
        val client = billingClient ?: return
        val productList = ProPlan.entries.map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val priceMap = mutableMapOf<ProPlan, String>()
                detailsList.forEach { details ->
                    productDetailsMap[details.productId] = details
                    val plan = ProPlan.entries.firstOrNull { it.productId == details.productId }
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                    if (plan != null && !price.isNullOrBlank()) {
                        priceMap[plan] = price
                    }
                }
                if (priceMap.isNotEmpty()) {
                    ProLicenseManager.updatePlanPrices(priceMap)
                }
            }
        }
    }

    private fun queryOwnedPurchases(onMessage: ((String) -> Unit)?) {
        val client = billingClient ?: return
        val ctx = appContext ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                var restoredAny = false
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        purchase.products.forEach { productId ->
                            val plan = ProPlan.entries.firstOrNull { it.productId == productId }
                            if (plan != null) {
                                handleVerifiedPurchase(ctx, purchase, plan)
                                restoredAny = true
                            }
                        }
                    }
                }
                // 本機若有配合 Google 帳號自動備份還原的綁定資料，亦一併刷新
                ProLicenseManager.refreshStatus(ctx)
                val status = ProLicenseManager.status.value
                onMessage?.invoke(
                    when {
                        restoredAny || status.isProPurchased ->
                            if (status.isUnlimited) "已恢復永久無限授權！"
                            else "已恢復授權（目前裝置已綁定，額度 ${status.boundDeviceCount}/${status.maxDevices} 台）"
                        status.deviceLimitReached ->
                            "此授權裝置數量已滿（${status.boundDeviceCount}/${status.maxDevices} 台），請重新購買授權或升級永久版"
                        else ->
                            "目前此 Google 帳號尚無有效授權紀錄"
                    }
                )
            } else {
                onMessage?.invoke("無法查詢購買紀錄：${result.debugMessage}")
            }
        }
    }

    fun launchPurchase(activity: Activity, plan: ProPlan, onMessage: (String) -> Unit) {
        pendingCallback = onMessage
        val client = billingClient ?: run {
            init(activity.applicationContext)
            billingClient
        }
        if (client == null || !client.isReady) {
            onMessage("正在連線 Google Play 商店服務，請稍後再試...")
            connectAndQuery {
                val details = productDetailsMap[plan.productId]
                if (details != null) {
                    activity.runOnUiThread { startBillingFlow(activity, details, onMessage) }
                }
            }
            return
        }
        val details = productDetailsMap[plan.productId]
        if (details == null) {
            queryAllProductDetails()
            onMessage("尚未在 Google Play Console 啟用商品 (${plan.productId})，或網路連線中，請稍後再試。")
            return
        }
        startBillingFlow(activity, details, onMessage)
    }

    private fun startBillingFlow(
        activity: Activity,
        details: ProductDetails,
        onMessage: (String) -> Unit
    ) {
        val client = billingClient ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val res = client.launchBillingFlow(activity, flowParams)
        if (res.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("無法開啟付款視窗：${res.debugMessage}")
        }
    }

    fun restorePurchases(context: Context, onMessage: (String) -> Unit) {
        init(context)
        connectAndQuery { queryOwnedPurchases(onMessage) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val ctx = appContext ?: return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                var unlockedPlan: ProPlan? = null
                list.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        purchase.products.forEach { productId ->
                            val plan = ProPlan.entries.firstOrNull { it.productId == productId }
                            if (plan != null) {
                                handleVerifiedPurchase(ctx, purchase, plan)
                                unlockedPlan = plan
                            }
                        }
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        pendingCallback?.invoke("付款處理中，完成後將自動解鎖授權")
                    }
                }
                unlockedPlan?.let { plan ->
                    val status = ProLicenseManager.status.value
                    val msg = if (plan.isUnlimited) {
                        "購買成功！已解鎖永久無限裝置授權"
                    } else {
                        "購買成功！已解鎖（裝置額度 ${status.boundDeviceCount}/${status.maxDevices} 台）"
                    }
                    pendingCallback?.invoke(msg)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingCallback?.invoke("已取消購買")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                ProLicenseManager.grantPlanPurchase(ctx, ProPlan.UNLIMITED)
                pendingCallback?.invoke("您已擁有永久授權，已自動恢復解鎖！")
            }
            else -> {
                pendingCallback?.invoke("購買失敗：${result.debugMessage}")
            }
        }
    }

    private fun handleVerifiedPurchase(context: Context, purchase: Purchase, plan: ProPlan) {
        ProLicenseManager.grantPlanPurchase(context, plan)
        val client = billingClient ?: return
        if (plan.isUnlimited) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(params) { }
            }
        } else {
            // 3 台 / 10 台裝置方案屬於「額度滿了可再重買」的消耗型商品
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.consumeAsync(params) { _, _ -> }
        }
    }
}
