package com.zango.pokertracker.billing

import com.android.billingclient.api.Purchase.PurchaseState
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoveAdsOwnershipTest {

    private val product = BillingManager.PRODUCT_REMOVE_ADS

    @Test
    fun `no purchases means not owned`() {
        assertEquals(RemoveAdsOwnership.NOT_OWNED, removeAdsOwnership(emptyList(), product))
    }

    @Test
    fun `a purchased product is owned`() {
        val purchases = listOf(PurchaseRecord(listOf(product), PurchaseState.PURCHASED))
        assertEquals(RemoveAdsOwnership.OWNED, removeAdsOwnership(purchases, product))
    }

    @Test
    fun `a pending purchase does not remove ads`() {
        val purchases = listOf(PurchaseRecord(listOf(product), PurchaseState.PENDING))
        assertEquals(RemoveAdsOwnership.PENDING, removeAdsOwnership(purchases, product))
    }

    @Test
    fun `an unspecified state is not owned`() {
        val purchases = listOf(PurchaseRecord(listOf(product), PurchaseState.UNSPECIFIED_STATE))
        assertEquals(RemoveAdsOwnership.NOT_OWNED, removeAdsOwnership(purchases, product))
    }

    @Test
    fun `another product does not count`() {
        val purchases = listOf(PurchaseRecord(listOf("something_else"), PurchaseState.PURCHASED))
        assertEquals(RemoveAdsOwnership.NOT_OWNED, removeAdsOwnership(purchases, product))
    }

    @Test
    fun `purchased wins over a later pending attempt`() {
        val purchases = listOf(
            PurchaseRecord(listOf(product), PurchaseState.PENDING),
            PurchaseRecord(listOf(product), PurchaseState.PURCHASED),
        )
        assertEquals(RemoveAdsOwnership.OWNED, removeAdsOwnership(purchases, product))
    }
}
