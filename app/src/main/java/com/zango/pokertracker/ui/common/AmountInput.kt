package com.zango.pokertracker.ui.common

import com.zango.pokertracker.R
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
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.Stakes

/**
 * Turning typed text into money and chips, with a message the host can act on when it fails.
 *
 * Shared by every amount field in the app so that the same mistake is reported the same way
 * whether it is made while setting a game up or while adding a rebuy three hours later.
 *
 * Messages are named rather than written: this runs outside a Composition, so it says which
 * sentence applies and the screen decides how to say it. The field's [label] travels as an
 * argument because it is itself a translated string.
 */

data class ParsedMoney(val money: Money? = null, val error: UiText? = null)

data class ParsedChips(val chips: Chips? = null, val error: UiText? = null)

fun parsePositiveMoney(text: String, label: UiText): ParsedMoney =
    when (val result = MoneyParser.parse(text)) {
        is MoneyParseResult.Valid ->
            if (result.money.isPositive) ParsedMoney(money = result.money)
            else ParsedMoney(error = UiText.of(R.string.error_amount_positive, label))

        is MoneyParseResult.Invalid -> ParsedMoney(error = result.error.describe(label))
    }

/** A figure outside [min]..[max], named with the field it was typed into; null when it fits. */
fun rangeError(amount: Money, min: Money, max: Money, label: UiText): UiText? = when {
    amount < min -> UiText.of(R.string.error_amount_below_min, label, UiText.cash(min))
    amount > max -> UiText.of(R.string.error_amount_above_max, label, UiText.cash(max))
    else -> null
}

/**
 * Reads a pair of blinds as typed: each within the range the app plays at, and the big blind above
 * the small one. The same check for a new game and for a level added in settings, so a level that
 * can be saved is always one a game can be started on.
 */
fun parseBlinds(smallText: String, bigText: String): ParsedBlinds {
    val smallLabel = UiText.of(R.string.create_small_blind)
    val bigLabel = UiText.of(R.string.create_big_blind)
    val (parsedSmall, smallParseError) = parsePositiveMoney(smallText, smallLabel)
    val (parsedBig, bigParseError) = parsePositiveMoney(bigText, bigLabel)
    val smallError = smallParseError
        ?: parsedSmall?.let { rangeError(it, Stakes.MIN_SMALL_BLIND, Stakes.MAX_SMALL_BLIND, smallLabel) }
    val bigError = bigParseError
        ?: parsedBig?.let { rangeError(it, Stakes.MIN_BIG_BLIND, Stakes.MAX_BIG_BLIND, bigLabel) }
        ?: UiText.of(R.string.error_big_blind_too_small).takeIf {
            parsedSmall != null && parsedBig != null && parsedBig <= parsedSmall
        }
    return ParsedBlinds(
        smallBlind = parsedSmall.takeIf { smallError == null },
        bigBlind = parsedBig.takeIf { bigError == null },
        smallBlindError = smallError,
        bigBlindError = bigError,
    )
}

data class ParsedBlinds(
    val smallBlind: Money?,
    val bigBlind: Money?,
    val smallBlindError: UiText?,
    val bigBlindError: UiText?,
)

/**
 * Reads a chip count. [allowZero] is true when counting a final stack, because busting out with
 * nothing is a real result, and false when buying in, because nobody buys in for no chips.
 */
fun parseChipCount(text: String, label: UiText, allowZero: Boolean): ParsedChips =
    when (val result = ChipsParser.parse(text)) {
        is ChipsParseResult.Valid -> when {
            result.chips.isPositive || (allowZero && result.chips.isZero) ->
                ParsedChips(chips = result.chips)

            else -> ParsedChips(error = UiText.of(R.string.error_amount_positive, label))
        }

        is ChipsParseResult.Invalid -> ParsedChips(error = result.error.describe(label))
    }

/**
 * A cash amount that is not a whole number of chips cannot be paid out at the table, so it is
 * refused where it is typed rather than left to surface as an unexplained gap at the end.
 */
fun wholeChipsError(amount: Money, rate: ChipRate): UiText? =
    when (val conversion = rate.chipsFor(amount)) {
        is ChipConversion.Exact -> null
        is ChipConversion.Inexact -> UiText.of(
            R.string.error_not_whole_chips,
            UiText.cash(amount),
            UiText.cash(rate.chipValue),
            UiText.cash(conversion.remainder),
        )
    }

fun MoneyParseError.describe(label: UiText): UiText = when (this) {
    MoneyParseError.EMPTY -> UiText.of(R.string.error_amount_required, label)
    MoneyParseError.MALFORMED -> UiText.of(R.string.error_amount_malformed, label)
    MoneyParseError.TOO_MANY_DECIMALS ->
        UiText.plural(R.plurals.error_amount_decimals, Money.MAX_SCALE, label, Money.MAX_SCALE)

    MoneyParseError.OUT_OF_RANGE -> UiText.of(R.string.error_amount_too_large, label)
}

fun ChipsParseError.describe(label: UiText): UiText = when (this) {
    ChipsParseError.EMPTY -> UiText.of(R.string.error_amount_required, label)
    ChipsParseError.MALFORMED -> UiText.of(R.string.error_chips_whole_number, label)
    ChipsParseError.NOT_WHOLE -> UiText.of(R.string.error_chips_not_whole)
    ChipsParseError.OUT_OF_RANGE -> UiText.of(R.string.error_amount_too_large, label)
}
