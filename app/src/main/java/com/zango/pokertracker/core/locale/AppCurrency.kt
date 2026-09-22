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
 * symbol is needed while resolving a message outside any screen, where nothing is injected. It
 * starts on the currency of the phone's region, which is right for almost everyone, so most hosts
 * never open the setting at all.
 */
object AppCurrencyStore {

    private const val PREFERENCES = "settings"
    private const val KEY_CURRENCY = "currency"
    private const val FALLBACK_CODE = "USD"

    private val _code = MutableStateFlow(FALLBACK_CODE)

    /** The ISO 4217 code in use, e.g. "EUR". */
    val code: StateFlow<String> = _code.asStateFlow()

    /** Reads the stored choice. Called once as the app starts, before anything is drawn. */
    fun init(context: Context) {
        _code.value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CURRENCY, null)
            ?.takeIf { isKnown(it) }
            ?: regionCode()
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
     * Every real currency the phone knows, by name in [locale]. The ones with codes starting in X
     * are left out: gold, testing codes and bank units, not anything a table is paid in.
     */
    fun options(locale: Locale): List<CurrencyOption> =
        Currency.getAvailableCurrencies()
            .filterNot { it.currencyCode.startsWith("X") }
            .map { CurrencyOption(it.currencyCode, it.getDisplayName(locale), symbolOf(it.currencyCode, locale)) }
            .sortedBy { it.name.lowercase(locale) }

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

    private fun regionCode(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
            ?.takeIf { isKnown(it) }
            ?: FALLBACK_CODE

    private fun isKnown(code: String): Boolean =
        runCatching { Currency.getInstance(code) }.isSuccess && !code.startsWith("X")
}
