package com.zango.pokertracker.billing

import com.android.billingclient.api.Purchase

/** Where the user stands with the "Remove ads" product, as far as Play has told the app. */
enum class RemoveAdsOwnership { NOT_OWNED, PENDING, OWNED }

/** The two facts about a purchase that decide ownership, apart from Play's own class. */
data class PurchaseRecord(val products: List<String>, val purchaseState: Int)

/**
 * Reads [purchases] for [productId]. Only [Purchase.PurchaseState.PURCHASED] counts as owned: a
 * pending payment (cash at a shop, a family approval) has not been paid for yet, and may never be.
 * An owned purchase wins over a pending one, so a second attempt left pending cannot hide the first.
 */
fun removeAdsOwnership(purchases: List<PurchaseRecord>, productId: String): RemoveAdsOwnership {
    val states = purchases.filter { productId in it.products }.map { it.purchaseState }
    return when {
        Purchase.PurchaseState.PURCHASED in states -> RemoveAdsOwnership.OWNED
        Purchase.PurchaseState.PENDING in states -> RemoveAdsOwnership.PENDING
        else -> RemoveAdsOwnership.NOT_OWNED
    }
}
