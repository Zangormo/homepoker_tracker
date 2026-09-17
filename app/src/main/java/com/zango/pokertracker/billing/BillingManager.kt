package com.zango.pokertracker.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.zango.pokertracker.billing.RemoveAdsBilling.LaunchResult
import com.zango.pokertracker.di.BillingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing for the one product the app sells: [PRODUCT_REMOVE_ADS], a non-consumable
 * that turns every ad off for good.
 *
 * [isAdsRemoved] is the single source of truth for that. There is no backend and no account, so
 * Play's own record is all there is: purchases arrive through [onPurchasesUpdated] while the app
 * runs, and [queryExistingPurchases] reads the full set on every connection and every return to
 * the foreground, which restores a purchase after a reinstall, on a second device, or once a
 * pending payment has cleared while the app was closed.
 *
 * The last confirmed answer is kept on disk only to seed [isAdsRemoved] at launch, so someone who
 * paid does not see a banner flash up in the second before Play replies. Play's reply always
 * overwrites it, a refund included.
 *
 * All state is touched on the main thread.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    @BillingPreferences private val preferences: SharedPreferences,
) : RemoveAdsBilling, PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Automatic service reconnection is deliberately not enabled. With it on, Google's guide says
    // startConnection must not be called from onBillingServiceDisconnected; this class runs its
    // own retry instead, so that a lost connection is re-established - and purchases re-read -
    // without waiting for the next API call.
    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val purchaseProcessing = Mutex()

    private val _isAdsRemoved = MutableStateFlow(preferences.getBoolean(KEY_ADS_REMOVED, false))

    override val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    // A cached purchase is already an answer; otherwise the first word from Play is awaited.
    private val _isEntitlementChecked = MutableStateFlow(_isAdsRemoved.value)

    /**
     * True once [isAdsRemoved] can be relied on for this launch: Play has answered, failed to, or
     * [ENTITLEMENT_WAIT_MILLIS] has passed. Ads wait for it, so someone who paid on another phone
     * is not shown one while Play is still being asked.
     */
    val isEntitlementChecked: StateFlow<Boolean> = _isEntitlementChecked.asStateFlow()

    private val _isPurchasePending = MutableStateFlow(false)

    override val isPurchasePending: StateFlow<Boolean> = _isPurchasePending.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    override val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private var connecting = false
    private var retryAttempt = 0
    private var retryJob: Job? = null

    init {
        scope.launch {
            delay(ENTITLEMENT_WAIT_MILLIS)
            _isEntitlementChecked.value = true
        }
    }

    /** Connects to Play if not connected or connecting already. Purchases are read once it is. */
    fun startConnection() {
        if (client.isReady || connecting) return
        connecting = true
        client.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        scope.launch {
            connecting = false
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    retryAttempt = 0
                    queryExistingPurchases()
                    loadPrice()
                }

                in RETRYABLE_CODES -> {
                    log("Billing setup failed, will retry", billingResult)
                    _isEntitlementChecked.value = true
                    scheduleReconnect()
                }

                // BILLING_UNAVAILABLE and the like: no Play Store, or a version too old. Retrying
                // will not change that; the next return to the foreground tries once more.
                else -> {
                    log("Billing unavailable", billingResult)
                    _isEntitlementChecked.value = true
                }
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        scope.launch {
            connecting = false
            scheduleReconnect()
        }
    }

    /**
     * Reads every one-time purchase Play holds for this user and makes [isAdsRemoved] match it.
     * Called when the connection is set up (so on every app start) and from
     * `MainActivity.onResume`. Without a connection it starts one, and the setup reads purchases.
     */
    fun queryExistingPurchases() {
        if (!client.isReady) {
            startConnection()
            return
        }
        scope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val response = client.queryPurchases(params)
            if (response.billingResult.responseCode != BillingResponseCode.OK) {
                // Keep what is known; a failed read is not evidence of a refund.
                log("Purchase query failed", response.billingResult)
                _isEntitlementChecked.value = true
                return@launch
            }
            processPurchases(response.purchases, isCompleteList = true)
        }
    }

    /**
     * Looks up [PRODUCT_REMOVE_ADS] and opens Play's purchase sheet over [activity]. The result of
     * the payment itself arrives later, through [onPurchasesUpdated].
     */
    override suspend fun launchPurchaseFlow(activity: Activity): LaunchResult {
        if (_isAdsRemoved.value) return LaunchResult.ALREADY_OWNED
        if (!client.isReady) {
            startConnection()
            return LaunchResult.UNAVAILABLE
        }
        val details = fetchRemoveAdsDetails() ?: return LaunchResult.UNAVAILABLE

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.purchaseOffer()?.offerToken?.let(productParams::setOfferToken)
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()

        // Google's guide requires the main thread, whatever the caller's dispatcher is.
        val result = withContext(Dispatchers.Main.immediate) { client.launchBillingFlow(activity, flowParams) }
        return when (result.responseCode) {
            BillingResponseCode.OK -> LaunchResult.LAUNCHED
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases()
                LaunchResult.ALREADY_OWNED
            }

            else -> {
                log("Could not launch the purchase flow", result)
                LaunchResult.UNAVAILABLE
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> if (purchases != null) {
                scope.launch { processPurchases(purchases, isCompleteList = false) }
            }

            BillingResponseCode.USER_CANCELED -> Unit
            // Bought before, perhaps on another device, and not yet known here.
            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { queryExistingPurchases() }
            else -> log("Purchase failed", billingResult)
        }
    }

    /**
     * Acknowledges and grants. [isCompleteList] is true for the result of a full query, which may
     * also take the entitlement away (a refund); an update from the listener only ever adds.
     */
    private suspend fun processPurchases(purchases: List<Purchase>, isCompleteList: Boolean) =
        purchaseProcessing.withLock {
            val ours = purchases.filter { PRODUCT_REMOVE_ADS in it.products }

            // Only a PURCHASED purchase is acknowledged. Play refunds one left unacknowledged for
            // three days; a failure here is retried by the next query, as isAcknowledged stays false.
            ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                .forEach { purchase ->
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val result = client.acknowledge(params)
                    if (result.responseCode != BillingResponseCode.OK) {
                        log("Acknowledge failed", result)
                    }
                }

            val ownership = removeAdsOwnership(
                ours.map { PurchaseRecord(it.products, it.purchaseState) },
                PRODUCT_REMOVE_ADS,
            )
            when (ownership) {
                RemoveAdsOwnership.OWNED -> {
                    setAdsRemoved(true)
                    _isPurchasePending.value = false
                }

                // PENDING is not paid for: ads stay exactly as they were.
                RemoveAdsOwnership.PENDING -> _isPurchasePending.value = true

                RemoveAdsOwnership.NOT_OWNED -> if (isCompleteList) {
                    setAdsRemoved(false)
                    _isPurchasePending.value = false
                }
            }
            _isEntitlementChecked.value = true
        }

    private fun setAdsRemoved(removed: Boolean) {
        _isAdsRemoved.value = removed
        preferences.edit { putBoolean(KEY_ADS_REMOVED, removed) }
    }

    private fun loadPrice() {
        scope.launch {
            fetchRemoveAdsDetails()
        }
    }

    /** Fresh product details from Play, refreshing [removeAdsPrice] on the way. */
    private suspend fun fetchRemoveAdsDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val response = client.queryProductDetails(params)
        if (response.billingResult.responseCode != BillingResponseCode.OK) {
            log("Product details query failed", response.billingResult)
            return null
        }
        val details = response.result.productDetailsList.firstOrNull { it.productId == PRODUCT_REMOVE_ADS }
        if (details == null) {
            // Not active in Play Console, or not available to this account or country.
            Log.w(TAG, "$PRODUCT_REMOVE_ADS not returned: ${response.result.unfetchedProductList}")
            return null
        }
        details.purchaseOffer()?.formattedPrice?.let { _removeAdsPrice.value = it }
        return details
    }

    /** The offer to sell, for a product with a single purchase option as this one has. */
    private fun ProductDetails.purchaseOffer(): ProductDetails.OneTimePurchaseOfferDetails? =
        oneTimePurchaseOfferDetailsList?.firstOrNull() ?: oneTimePurchaseOfferDetails

    private fun scheduleReconnect() {
        if (retryJob?.isActive == true) return
        if (retryAttempt >= MAX_RECONNECT_ATTEMPTS) {
            // Give up quietly; queryExistingPurchases from the next onResume starts over.
            retryAttempt = 0
            return
        }
        val backoff = (INITIAL_RECONNECT_DELAY_MILLIS shl retryAttempt).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
        retryAttempt++
        retryJob = scope.launch {
            delay(backoff)
            startConnection()
        }
    }

    private fun log(message: String, result: BillingResult) {
        Log.w(TAG, "$message: ${result.responseCode} ${result.debugMessage}")
    }

    companion object {
        /** The product ID set up in Play Console. */
        const val PRODUCT_REMOVE_ADS = "remove_ads"

        private const val TAG = "Billing"
        private const val KEY_ADS_REMOVED = "ads_removed"
        private const val ENTITLEMENT_WAIT_MILLIS = 5_000L
        private const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 6

        private val RETRYABLE_CODES = setOf(
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.ERROR,
        )
    }
}
