package com.zango.pokertracker.ui.creategame

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.ChipRateDerivation
import com.zango.pokertracker.core.money.ChipRateError
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.domain.model.NameRules
import com.zango.pokertracker.domain.model.NewGameEntry
import com.zango.pokertracker.domain.model.NewGameSetup
import com.zango.pokertracker.ui.common.parseChipCount
import com.zango.pokertracker.ui.common.parsePositiveMoney
import com.zango.pokertracker.ui.common.wholeChipsError

/** Hosts think of a buy-in either as "100 big blinds" or as a flat cash figure. Both are offered. */
enum class BuyInMode { BIG_BLINDS, CASH }

/**
 * Exactly what the host has typed, before any interpretation.
 *
 * Kept as raw strings rather than parsed values so a half-typed "0." is never thrown away
 * mid-keystroke, and so validation is a pure function of the form that tests can drive directly.
 */
data class CreateGameForm(
    val name: String = "",
    val smallBlind: String = "",
    val bigBlind: String = "",
    /** When true the chip value is derived from how the chips are marked, rather than typed. */
    val deriveChipValue: Boolean = true,
    val chipValue: String = "",
    val chipsPerBigBlind: String = "",
    val buyInMode: BuyInMode = BuyInMode.BIG_BLINDS,
    val buyInBigBlinds: String = NewGameSetup.DEFAULT_BUY_IN_BIG_BLINDS.toString(),
    val buyInCash: String = "",
    val payoutRounding: String = "0.01",
    /**
     * Selected players in the order they were tapped, mapped to their buy-in override. A null
     * value means "use the game default", which is different from an override that happens to
     * equal the default: the default still follows if the host edits it afterwards.
     */
    val selection: Map<Long, Money?> = emptyMap(),
)

/**
 * The outcome of checking a form: a message for every field that is wrong, the values that did
 * parse (so the screen can show live previews even while other fields are incomplete), and a
 * ready-to-persist [setup] that is non-null only when everything holds together.
 */
data class CreateGameValidation(
    val nameError: UiText? = null,
    val smallBlindError: UiText? = null,
    val bigBlindError: UiText? = null,
    val chipValueError: UiText? = null,
    val buyInError: UiText? = null,
    val payoutRoundingError: UiText? = null,
    val playersError: UiText? = null,
    val overrideErrors: Map<Long, UiText> = emptyMap(),
    val smallBlind: Money? = null,
    val bigBlind: Money? = null,
    val chipRate: ChipRate? = null,
    val defaultBuyIn: Money? = null,
    val payoutRounding: Money? = null,
    val setup: NewGameSetup? = null,
) {
    val isValid: Boolean get() = setup != null
}

fun CreateGameForm.validate(): CreateGameValidation {
    val nameError: UiText? = when {
        name.isBlank() -> UiText.of(R.string.error_game_name_required)
        NameRules.isTooLong(name) ->
            NameRules.tooLongMessage(UiText.of(R.string.error_name_label_game))

        else -> null
    }

    val (smallBlindValue, smallBlindError) =
        parsePositiveMoney(smallBlind, UiText.of(R.string.create_small_blind))
    val (parsedBigBlind, bigBlindParseError) =
        parsePositiveMoney(bigBlind, UiText.of(R.string.create_big_blind))
    val bigBlindError: UiText? = bigBlindParseError ?: when {
        smallBlindValue != null && parsedBigBlind != null && parsedBigBlind <= smallBlindValue ->
            UiText.of(R.string.error_big_blind_too_small)

        else -> null
    }
    val bigBlindValue = if (bigBlindError == null) parsedBigBlind else null

    val (chipRate, chipValueError) = resolveChipRate(bigBlindValue)
    val (defaultBuyIn, buyInError) = resolveDefaultBuyIn(bigBlindValue, chipRate)
    val (roundingValue, payoutRoundingError) =
        parsePositiveMoney(payoutRounding, UiText.of(R.string.label_rounding_unit))

    val playersError =
        if (selection.isEmpty()) UiText.of(R.string.error_pick_a_player) else null
    val overrideErrors = buildMap {
        if (chipRate == null) return@buildMap
        selection.forEach { (playerId, override) ->
            if (override == null) return@forEach
            val error = when {
                !override.isPositive ->
                    UiText.of(R.string.error_buy_in_positive)

                else -> wholeChipsError(override, chipRate)
            }
            if (error != null) put(playerId, error)
        }
    }

    val everythingHolds = nameError == null && smallBlindError == null && bigBlindError == null &&
        chipValueError == null && buyInError == null && payoutRoundingError == null &&
        playersError == null && overrideErrors.isEmpty()

    val setup = if (
        everythingHolds && smallBlindValue != null && bigBlindValue != null && chipRate != null &&
        defaultBuyIn != null && roundingValue != null
    ) {
        NewGameSetup(
            name = name.trim(),
            smallBlind = smallBlindValue,
            bigBlind = bigBlindValue,
            chipRate = chipRate,
            defaultBuyIn = defaultBuyIn,
            payoutRounding = roundingValue,
            entries = selection.map { (playerId, override) ->
                NewGameEntry(playerId = playerId, buyIn = override ?: defaultBuyIn)
            },
        )
    } else {
        null
    }

    return CreateGameValidation(
        nameError = nameError,
        smallBlindError = smallBlindError,
        bigBlindError = bigBlindError,
        chipValueError = chipValueError,
        buyInError = buyInError,
        payoutRoundingError = payoutRoundingError,
        playersError = playersError,
        overrideErrors = overrideErrors,
        smallBlind = smallBlindValue,
        bigBlind = bigBlindValue,
        chipRate = chipRate,
        defaultBuyIn = defaultBuyIn,
        payoutRounding = roundingValue,
        setup = setup,
    )
}

private fun CreateGameForm.resolveChipRate(bigBlind: Money?): Pair<ChipRate?, UiText?> {
    if (!deriveChipValue) {
        val (value, error) = parsePositiveMoney(chipValue, UiText.of(R.string.label_chip_value))
        return value?.let { ChipRate(it.micros) } to error
    }

    val (chips, chipsError) = parseChipCount(
        chipsPerBigBlind,
        UiText.of(R.string.label_big_blind_in_chips),
        allowZero = false,
    )
    if (chipsError != null) return null to chipsError
    if (bigBlind == null) return null to UiText.of(R.string.error_blinds_first)

    return when (val derived = ChipRate.derive(bigBlind, chips!!)) {
        is ChipRateDerivation.Valid -> derived.rate to null
        is ChipRateDerivation.Invalid -> null to when (derived.error) {
            ChipRateError.NOT_DIVISIBLE -> UiText.of(
                R.string.error_chip_split,
                bigBlind.format(),
                chips.count,
            )

            ChipRateError.NON_POSITIVE_CASH -> UiText.of(R.string.error_blinds_first)
            ChipRateError.NON_POSITIVE_CHIPS ->
                UiText.of(R.string.error_chips_per_bb_positive)
        }
    }
}

private fun CreateGameForm.resolveDefaultBuyIn(
    bigBlind: Money?,
    chipRate: ChipRate?,
): Pair<Money?, UiText?> {
    val (amount, parseError) = when (buyInMode) {
        BuyInMode.CASH -> parsePositiveMoney(buyInCash, UiText.of(R.string.label_buy_in))
            .let { it.money to it.error }

        BuyInMode.BIG_BLINDS -> {
            val multiple = buyInBigBlinds.trim().toLongOrNull()
            when {
                buyInBigBlinds.isBlank() -> null to UiText.of(R.string.error_buy_in_required)
                multiple == null -> null to UiText.of(R.string.error_buy_in_whole_big_blinds)
                multiple <= 0 -> null to UiText.of(R.string.error_buy_in_positive)
                bigBlind == null -> null to UiText.of(R.string.error_blinds_first)
                else -> runCatching { bigBlind * multiple }.getOrNull() to null
            }
        }
    }
    if (parseError != null || amount == null) return null to parseError
    val chipError = chipRate?.let { wholeChipsError(amount, it) }
    return if (chipError != null) null to chipError else amount to null
}
