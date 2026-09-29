package com.zango.pokertracker.core.locale

import android.content.Context
import android.icu.util.ULocale
import android.os.Build
import androidx.core.content.edit
import com.zango.pokertracker.core.money.CashFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** One currency on offer in settings. */
data class CurrencyOption(val code: String, val name: String, val symbol: String)

/**
 * The currency the host keeps their games in, as a symbol shown beside every cash amount.
 *
 * Kept next to the language, in the same preferences and the same way: a plain object, because the
 * symbol is needed while resolving a message outside any screen, where nothing is injected. Until
 * the host picks one it is the US dollar, whatever region the phone is set to.
 */
object AppCurrencyStore {

    private const val PREFERENCES = "settings"
    private const val KEY_CURRENCY = "currency"
    private const val DEFAULT_CODE = "USD"

    private val _code = MutableStateFlow(DEFAULT_CODE)

    /** The ISO 4217 code in use, e.g. "EUR". */
    val code: StateFlow<String> = _code.asStateFlow()

    /** Reads the stored choice. Called once as the app starts, before anything is drawn. */
    fun init(context: Context) {
        _code.value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CURRENCY, null)
            ?.takeIf { isKnown(it) }
            ?: DEFAULT_CODE
    }

    fun set(context: Context, code: String) {
        if (!isKnown(code)) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(KEY_CURRENCY, code) }
        _code.value = code
    }

    /** How to write cash in [code], in the language the app is showing. */
    fun cashFormat(code: String = _code.value, locale: Locale): CashFormat =
        CashFormat(symbol = symbolOf(code, locale), symbolFirst = symbolComesFirst(locale))

    /**
     * The currencies on offer, by name in [locale]: one per symbol, since the symbol is all an
     * amount shows. The ones with codes starting in X are left out: gold, testing codes and bank
     * units, not anything a table is paid in.
     */
    fun options(locale: Locale): List<CurrencyOption> =
        uniqueSymbols(
            Currency.getAvailableCurrencies()
                .filterNot { it.currencyCode.startsWith("X") }
                .map { CurrencyOption(it.currencyCode, it.getDisplayName(locale), symbolOf(it.currencyCode, locale)) },
        ).sortedBy { it.name.lowercase(locale) }

    /**
     * Keeps only currencies with a sign of their own. One whose symbol is just its code ("CHF")
     * has none; where several share a sign, as the Canadian and Australian dollars share "$",
     * only the one people mean by it stays: the first in [SYMBOL_OWNERS], else the lowest code.
     */
    internal fun uniqueSymbols(options: List<CurrencyOption>): List<CurrencyOption> =
        options
            .filter { it.symbol.isNotBlank() && !it.symbol.equals(it.code, ignoreCase = true) }
            .groupBy { it.symbol }
            .values
            .map { sharing -> sharing.minWith(compareBy({ ownerRank(it.code) }, { it.code })) }

    private fun ownerRank(code: String): Int =
        SYMBOL_OWNERS.indexOf(code).takeIf { it >= 0 } ?: Int.MAX_VALUE

    /** Who a shared sign belongs to, most used first: "$" is the US dollar, "£" the pound. */
    private val SYMBOL_OWNERS = listOf(
        "USD", "EUR", "GBP", "JPY", "CNY", "INR", "RUB", "KRW", "SEK", "BRL", "MXN", "TRY", "PLN",
        "UAH", "KZT", "ILS", "NGN", "PHP", "VND", "THB",
    )

    fun option(code: String, locale: Locale): CurrencyOption {
        val currency = Currency.getInstance(code)
        return CurrencyOption(code, currency.getDisplayName(locale), symbolOf(code, locale))
    }

    /**
     * The short symbol people actually write: "€", "₽", "zł". Android 11 added the narrow form,
     * which drops the country prefix older versions put on a dollar abroad ("US$"); before that
     * the ordinary symbol is the best there is.
     */
    private fun symbolOf(code: String, locale: Locale): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.icu.util.Currency.getInstance(code)
                .getName(ULocale.forLocale(locale), android.icu.util.Currency.NARROW_SYMBOL_NAME, null)
        } else {
            Currency.getInstance(code).getSymbol(locale)
        }

    /** Whether [locale] writes the symbol before the number, read from its own currency pattern. */
    private fun symbolComesFirst(locale: Locale): Boolean {
        val pattern = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern()
            ?: return true
        return pattern.trimStart().startsWith("¤")
    }

    private fun isKnown(code: String): Boolean =
        runCatching { Currency.getInstance(code) }.isSuccess && !code.startsWith("X")
}
