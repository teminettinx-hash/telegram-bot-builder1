package com.botbuilder.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.botbuilder.app.data.local.SecureStore

/**
 * Thin wrapper around Play Billing for the two subscription products (Pro, Max),
 * each with monthly/yearly base plans. Callback-based (not the ktx suspend helpers)
 * so it compiles against the plain Billing Library API without version guesswork.
 *
 * SETUP REQUIRED IN PLAY CONSOLE before this does anything real:
 *  Monetize → Subscriptions → create products with IDs from [BillingConfig],
 *  each with a monthly and a yearly base plan matching the *_BASE_PLAN constants.
 */
class BillingManager(
    private val context: Context,
    private val secureStore: SecureStore,
    private val onTierChanged: (PlanTier) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var isReady = false

    fun startConnection(onReady: () -> Unit = {}) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                isReady = result.responseCode == BillingClient.BillingResponseCode.OK
                if (isReady) {
                    refreshEntitlement()
                    onReady()
                } else {
                    Log.e("BillingManager", "Setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isReady = false
            }
        })
    }

    /** Re-checks active subscriptions with Play and updates the cached tier.
     *  Call on app start and whenever a purchase completes, so entitlement is
     *  never stale (this is what actually stops "everything being free"). */
    fun refreshEntitlement() {
        if (!isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            var highestTier = PlanTier.FREE
            for (purchase in purchases) {
                if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                for (productId in purchase.products) {
                    val tier = PlanTier.fromProductId(productId) ?: continue
                    if (tier.ordinal > highestTier.ordinal) highestTier = tier
                }
                if (!purchase.isAcknowledged) acknowledge(purchase)
            }

            secureStore.planTier = highestTier.name
            onTierChanged(highestTier)
        }
    }

    fun queryOffers(onResult: (List<ProductDetails>) -> Unit) {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConfig.PRO_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConfig.MAX_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(productDetailsResult.productDetailsList)
            } else {
                Log.e("BillingManager", "queryProductDetailsAsync failed: ${result.debugMessage}")
                onResult(emptyList())
            }
        }
    }

    /** Launches the purchase sheet for a specific base plan (e.g. "pro-monthly"). */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, basePlanId: String) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId }
            ?.offerToken
            ?: run {
                Log.e("BillingManager", "No offer found for base plan $basePlanId — check Play Console setup")
                return
            }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            refreshEntitlement()
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
