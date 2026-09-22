package com.zango.pokertracker.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class CashFormatTest {

    @Test
    fun `a symbol that goes first sits right against the number`() {
        assertEquals("€4.50", CashFormat("€", symbolFirst = true).format("4.50"))
    }

    @Test
    fun `a symbol that goes last is separated by a space`() {
        assertEquals("4.50 €", CashFormat("€", symbolFirst = false).format("4.50"))
    }

    @Test
    fun `a written-out symbol in front keeps a space so it does not run into the digits`() {
        assertEquals("CHF 4.50", CashFormat("CHF", symbolFirst = true).format("4.50"))
    }

    @Test
    fun `no symbol leaves the figure alone`() {
        assertEquals("4.50", CashFormat.NONE.format("4.50"))
    }

    @Test
    fun `money is formatted the same way the rest of the app writes it`() {
        assertEquals("$1.50", CashFormat("$", symbolFirst = true).format(Money(1_500_000)))
    }
}
