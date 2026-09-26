package com.zango.pokertracker.billing

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/*
 * Suspend versions of the BillingClient calls the app makes.
 *
 * billing-ktx provides these, but every release that satisfies Play's v8+ requirement from 8.1.0
 * on is compiled with Kotlin 2.2-2.3 and pulls in kotlin-stdlib 2.2.10, which the Kotlin 2.0
 * compiler this project uses cannot read. The plain Java `billing` artifact has no Kotlin in it, so
 * it is used at the recommended version and these few lines stand in for the ktx module. Drop this
 * file for billing-ktx together with the Kotlin 2.2+ upgrade noted in libs.versions.toml.
 *
 * Each listener is called exactly once by the library, so resuming unconditionally is safe; a
 * result arriving after cancellation is simply dropped.
 */

internal class PurchasesQueryResult(val billingResult: BillingResult, val purchases: List<Purchase>)

internal class ProductDetailsQueryResult(
    val billingResult: BillingResult,
    val result: QueryProductDetailsResult,
)

internal suspend fun BillingClient.queryPurchases(params: QueryPurchasesParams): PurchasesQueryResult =
    suspendCancellableCoroutine { continuation ->
        queryPurchasesAsync(params) { billingResult, purchases ->
            if (continuation.isActive) continuation.resume(PurchasesQueryResult(billingResult, purchases))
        }
    }

internal suspend fun BillingClient.queryProductDetails(params: QueryProductDetailsParams): ProductDetailsQueryResult =
    suspendCancellableCoroutine { continuation ->
        queryProductDetailsAsync(params) { billingResult, result ->
            if (continuation.isActive) continuation.resume(ProductDetailsQueryResult(billingResult, result))
        }
    }

internal suspend fun BillingClient.acknowledge(params: AcknowledgePurchaseParams): BillingResult =
    suspendCancellableCoroutine { continuation ->
        acknowledgePurchase(params) { billingResult ->
            if (continuation.isActive) continuation.resume(billingResult)
        }
    }

/** Tester tools only: the app never consumes "Remove ads" otherwise. */
internal suspend fun BillingClient.consume(params: ConsumeParams): BillingResult =
    suspendCancellableCoroutine { continuation ->
        consumeAsync(params) { billingResult, _ ->
            if (continuation.isActive) continuation.resume(billingResult)
        }
    }
