package org.acoustixaudio.casttobrowser.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady = _isBillingReady.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro = _isPro.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails = _productDetails.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError = _billingError.asStateFlow()

    private val _purchaseMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val purchaseMessage = _purchaseMessage.asSharedFlow()

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isBillingReady.value = true
                    queryProductDetails()
                    queryActivePurchases(feedbackMode = FeedbackMode.NONE)
                } else {
                    _billingError.value = result.debugMessage
                }
            }

            override fun onBillingServiceDisconnected() {
                _isBillingReady.value = false
            }
        })
    }

    fun disconnect() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    fun launchProPurchase(activity: Activity) {
        val details = _productDetails.value ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            _billingError.value = "Billing is not ready yet."
            _purchaseMessage.tryEmit("Billing not ready. Try again in a moment.")
            return
        }
        queryActivePurchases(feedbackMode = FeedbackMode.RESTORE)
    }

    private fun queryProductDetails() {
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.firstOrNull()
            } else {
                _billingError.value = result.debugMessage
            }
        }
    }

    private fun queryActivePurchases(feedbackMode: FeedbackMode) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val listener = PurchasesResponseListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases, feedbackMode)
            } else if (feedbackMode == FeedbackMode.RESTORE) {
                _purchaseMessage.tryEmit("Could not restore purchase. Please try again.")
            }
        }
        billingClient.queryPurchasesAsync(params, listener)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases, feedbackMode = FeedbackMode.PURCHASE)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseMessage.tryEmit("Purchase canceled.")
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _billingError.value = result.debugMessage
            _purchaseMessage.tryEmit("Purchase failed. Please try again.")
        }
    }

    private fun processPurchases(purchases: List<Purchase>, feedbackMode: FeedbackMode) {
        val hasPro = purchases.any { purchase ->
            purchase.products.contains(PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        _isPro.value = hasPro

        if (feedbackMode != FeedbackMode.NONE) {
            _purchaseMessage.tryEmit(
                when {
                    hasPro && feedbackMode == FeedbackMode.PURCHASE -> "Pro unlocked successfully."
                    hasPro && feedbackMode == FeedbackMode.RESTORE -> "Pro purchase restored."
                    purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> "Purchase pending approval."
                    else -> "No Pro purchase found on this account."
                }
            )
        }

        purchases
            .filter { purchase ->
                purchase.products.contains(PRO_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !purchase.isAcknowledged
            }
            .forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { acknowledgeResult ->
                    if (acknowledgeResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        _billingError.value = acknowledgeResult.debugMessage
                    }
                }
            }
    }

    companion object {
        const val PRO_PRODUCT_ID = "pro"
    }

    private enum class FeedbackMode {
        NONE,
        RESTORE,
        PURCHASE
    }
}





