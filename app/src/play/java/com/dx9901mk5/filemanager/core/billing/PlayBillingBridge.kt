package com.dx9901mk5.filemanager.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

object PlayBillingBridge : PurchasesUpdatedListener {
    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var proProductDetails: ProductDetails? = null
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
                    queryProductDetails()
                    queryOwnedPurchases(null)
                    onReady?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                // 下次操作時自動重連
            }
        })
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(ProLicenseManager.PRO_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = detailsList.firstOrNull()
                proProductDetails = details
                val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
                ProLicenseManager.updateProductPriceLabel(price)
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
                val valid = purchases.any { p ->
                    p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        ProLicenseManager.PRO_PRODUCT_ID in p.products
                }
                if (valid) {
                    purchases.filter { !it.isAcknowledged && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { acknowledge(it) }
                    ProLicenseManager.setProPurchased(ctx, true)
                    onMessage?.invoke("已恢復 Pro 正式版授權！")
                } else {
                    onMessage?.invoke("目前此 Google 帳號尚無購買紀錄")
                }
            } else {
                onMessage?.invoke("無法查詢購買紀錄：${result.debugMessage}")
            }
        }
    }

    fun launchPurchase(activity: Activity, onMessage: (String) -> Unit) {
        pendingCallback = onMessage
        val client = billingClient ?: run {
            init(activity.applicationContext)
            billingClient
        }
        if (client == null || !client.isReady) {
            onMessage("正在連線 Google Play 商店服務，請稍後再試...")
            connectAndQuery {
                val details = proProductDetails
                if (details != null) {
                    activity.runOnUiThread { startBillingFlow(activity, details, onMessage) }
                }
            }
            return
        }
        val details = proProductDetails
        if (details == null) {
            queryProductDetails()
            onMessage("尚未在 Google Play Console 啟用商品 (${ProLicenseManager.PRO_PRODUCT_ID})，或網路連線中，請稍後再試。")
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
                var unlocked = false
                list.forEach { purchase ->
                    if (ProLicenseManager.PRO_PRODUCT_ID in purchase.products) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (!purchase.isAcknowledged) acknowledge(purchase)
                            ProLicenseManager.setProPurchased(ctx, true)
                            unlocked = true
                        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                            pendingCallback?.invoke("付款處理中，完成後將自動解鎖 Pro 功能")
                        }
                    }
                }
                if (unlocked) {
                    pendingCallback?.invoke("購買成功！已解鎖全部 Pro 專業功能")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingCallback?.invoke("已取消購買")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                ProLicenseManager.setProPurchased(ctx, true)
                pendingCallback?.invoke("您已擁有 Pro 版，已自動恢復解鎖！")
            }
            else -> {
                pendingCallback?.invoke("購買失敗：${result.debugMessage}")
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { }
    }
}
