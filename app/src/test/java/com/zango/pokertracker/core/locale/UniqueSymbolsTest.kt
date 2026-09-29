package com.zango.pokertracker.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueSymbolsTest {

    private fun option(code: String, symbol: String) = CurrencyOption(code, code, symbol)

    private fun codes(vararg options: CurrencyOption) =
        AppCurrencyStore.uniqueSymbols(options.toList()).map { it.code }.sorted()

    @Test
    fun `a shared dollar sign stays with the US dollar`() {
        assertEquals(
            listOf("USD"),
            codes(option("AUD", "$"), option("CAD", "$"), option("USD", "$"), option("NZD", "$")),
        )
    }

    @Test
    fun `a shared pound sign stays with sterling`() {
        assertEquals(listOf("GBP"), codes(option("EGP", "£"), option("GBP", "£"), option("FKP", "£")))
    }

    @Test
    fun `a currency whose symbol is only its code is left out`() {
        assertEquals(listOf("EUR"), codes(option("CHF", "CHF"), option("EUR", "€")))
    }

    @Test
    fun `signs of their own all stay`() {
        assertEquals(
            listOf("EUR", "RUB", "USD"),
            codes(option("EUR", "€"), option("RUB", "₽"), option("USD", "$")),
        )
    }

    @Test
    fun `a sign no listed currency owns goes to the lowest code`() {
        assertEquals(listOf("DKK"), codes(option("NOK", "kr"), option("DKK", "kr"), option("ISK", "kr")))
    }
}
