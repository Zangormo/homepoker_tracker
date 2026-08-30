package com.zango.pokertracker.ui.common

import com.zango.pokertracker.core.money.ChipConversion
import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.ChipsParseError
import com.zango.pokertracker.core.money.ChipsParseResult
import com.zango.pokertracker.core.money.ChipsParser
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.core.money.MoneyParseError
import com.zango.pokertracker.core.money.MoneyParseResult
import com.zango.pokertracker.core.money.MoneyParser

/**
 * Turning typed text into money and chips, with a message the host can act on when it fails.
 *
 * Shared by every amount field in the app so that the same mistake is reported the same way
 * whether it is made while setting a game up or while adding a rebuy three hours later.
 */

data class ParsedMoney(val money: Money? = null, val error: String? = null)

data class ParsedChips(val chips: Chips? = null, val error: String? = null)

fun parsePositiveMoney(text: String, label: String): ParsedMoney =
    when (val result = MoneyParser.parse(text)) {
        is MoneyParseResult.Valid ->
            if (result.money.isPositive) ParsedMoney(money = result.money)
            else ParsedMoney(error = "$label must be greater than zero")

        is MoneyParseResult.Invalid -> ParsedMoney(error = result.error.describe(label))
    }

/**
 * Reads a chip count. [allowZero] is true when counting a final stack, because busting out with
 * nothing is a real result, and false when buying in, because nobody buys in for no chips.
 */
fun parseChipCount(text: String, label: String, allowZero: Boolean): ParsedChips =
    when (val result = ChipsParser.parse(text)) {
        is ChipsParseResult.Valid -> when {
            result.chips.isPositive || (allowZero && result.chips.isZero) ->
                ParsedChips(chips = result.chips)

            else -> ParsedChips(error = "$label must be greater than zero")
        }

        is ChipsParseResult.Invalid -> ParsedChips(error = result.error.describe(label))
    }

/**
 * A cash amount that is not a whole number of chips cannot be paid out at the table, so it is
 * refused where it is typed rather than left to surface as an unexplained gap at the end.
 */
fun wholeChipsError(amount: Money, rate: ChipRate): String? =
    when (val conversion = rate.chipsFor(amount)) {
        is ChipConversion.Exact -> null
        is ChipConversion.Inexact ->
            "${amount.format()} is not a whole number of ${rate.chipValue.format()} chips " +
                "(${conversion.remainder.format()} left over)"
    }

fun MoneyParseError.describe(label: String): String = when (this) {
    MoneyParseError.EMPTY -> "$label is required"
    MoneyParseError.MALFORMED -> "Enter $label as a number, for example 0.005"
    MoneyParseError.TOO_MANY_DECIMALS -> "$label can have at most ${Money.MAX_SCALE} decimals"
    MoneyParseError.OUT_OF_RANGE -> "$label is too large"
}

fun ChipsParseError.describe(label: String): String = when (this) {
    ChipsParseError.EMPTY -> "$label is required"
    ChipsParseError.MALFORMED -> "Enter $label as a whole number"
    ChipsParseError.NOT_WHOLE -> "Chips come in whole numbers only"
    ChipsParseError.OUT_OF_RANGE -> "$label is too large"
}
