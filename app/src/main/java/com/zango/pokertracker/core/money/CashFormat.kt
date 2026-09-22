package com.zango.pokertracker.core.money

/**
 * How a cash amount is written with its currency: which symbol, and on which side of the number.
 *
 * Display only. Amounts are stored and calculated as plain [Money]; the currency is a label the
 * host picks in settings, so changing it never changes a figure.
 *
 * The side follows the app's language, the way every banking and shopping app does it: "€4.50" in
 * English, "4.50 €" in German or French.
 */
data class CashFormat(val symbol: String, val symbolFirst: Boolean) {

    fun format(amount: String): String = when {
        symbol.isEmpty() -> amount
        // Letters run into digits ("CHF4.50"), so a written-out symbol keeps a space before them.
        symbolFirst && symbol.last().isLetter() -> "$symbol $amount"
        symbolFirst -> "$symbol$amount"
        else -> "$amount $symbol"
    }

    fun format(money: Money): String = format(money.format())

    companion object {
        /** No symbol at all: the bare figure, for previews and anywhere a currency is not known. */
        val NONE = CashFormat(symbol = "", symbolFirst = true)
    }
}
