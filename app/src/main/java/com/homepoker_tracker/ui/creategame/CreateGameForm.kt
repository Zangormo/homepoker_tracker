package com.homepoker_tracker.ui.creategame

import com.homepoker_tracker.core.money.ChipRate
import com.homepoker_tracker.core.money.ChipRateDerivation
import com.homepoker_tracker.core.money.ChipRateError
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.domain.model.NewGameEntry
import com.homepoker_tracker.domain.model.NewGameSetup
import com.homepoker_tracker.ui.common.parseChipCount
import com.homepoker_tracker.ui.common.parsePositiveMoney
import com.homepoker_tracker.ui.common.wholeChipsError

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
    val nameError: String? = null,
    val smallBlindError: String? = null,
    val bigBlindError: String? = null,
    val chipValueError: String? = null,
    val buyInError: String? = null,
    val payoutRoundingError: String? = null,
    val playersError: String? = null,
    val overrideErrors: Map<Long, String> = emptyMap(),
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
    val nameError = if (name.isBlank()) "Give the game a name" else null

    val (smallBlindValue, smallBlindError) = parsePositiveMoney(smallBlind, "Small blind")
    val (parsedBigBlind, bigBlindParseError) = parsePositiveMoney(bigBlind, "Big blind")
    val bigBlindError = bigBlindParseError ?: when {
        smallBlindValue != null && parsedBigBlind != null && parsedBigBlind <= smallBlindValue ->
            "Big blind must be larger than the small blind"

        else -> null
    }
    val bigBlindValue = if (bigBlindError == null) parsedBigBlind else null

    val (chipRate, chipValueError) = resolveChipRate(bigBlindValue)
    val (defaultBuyIn, buyInError) = resolveDefaultBuyIn(bigBlindValue, chipRate)
    val (roundingValue, payoutRoundingError) = parsePositiveMoney(payoutRounding, "Rounding unit")

    val playersError = if (selection.isEmpty()) "Pick at least one player" else null
    val overrideErrors = buildMap {
        if (chipRate == null) return@buildMap
        selection.forEach { (playerId, override) ->
            if (override == null) return@forEach
            val error = when {
                !override.isPositive -> "A buy-in must be greater than zero"
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

/** The effective buy-in for a selected player: their override, or the game default. */
fun CreateGameForm.buyInFor(playerId: Long, defaultBuyIn: Money?): Money? =
    selection[playerId] ?: defaultBuyIn

private fun CreateGameForm.resolveChipRate(bigBlind: Money?): Pair<ChipRate?, String?> {
    if (!deriveChipValue) {
        val (value, error) = parsePositiveMoney(chipValue, "Chip value")
        return value?.let { ChipRate(it.micros) } to error
    }

    val (chips, chipsError) = parseChipCount(chipsPerBigBlind, "Big blind in chips", allowZero = false)
    if (chipsError != null) return null to chipsError
    if (bigBlind == null) return null to "Enter the blinds first"

    return when (val derived = ChipRate.derive(bigBlind, chips!!)) {
        is ChipRateDerivation.Valid -> derived.rate to null
        is ChipRateDerivation.Invalid -> null to when (derived.error) {
            ChipRateError.NOT_DIVISIBLE ->
                "A big blind of ${bigBlind.format()} does not split evenly into $chips chips"

            ChipRateError.NON_POSITIVE_CASH -> "Enter the blinds first"
            ChipRateError.NON_POSITIVE_CHIPS -> "Big blind in chips must be greater than zero"
        }
    }
}

private fun CreateGameForm.resolveDefaultBuyIn(
    bigBlind: Money?,
    chipRate: ChipRate?,
): Pair<Money?, String?> {
    val (amount, parseError) = when (buyInMode) {
        BuyInMode.CASH -> parsePositiveMoney(buyInCash, "Buy-in").let { it.money to it.error }
        BuyInMode.BIG_BLINDS -> {
            val multiple = buyInBigBlinds.trim().toLongOrNull()
            when {
                buyInBigBlinds.isBlank() -> null to "Buy-in is required"
                multiple == null -> null to "Enter the buy-in as a whole number of big blinds"
                multiple <= 0 -> null to "Buy-in must be greater than zero"
                bigBlind == null -> null to "Enter the blinds first"
                else -> runCatching { bigBlind * multiple }.getOrNull() to null
            }
        }
    }
    if (parseError != null || amount == null) return null to parseError
    val chipError = chipRate?.let { wholeChipsError(amount, it) }
    return if (chipError != null) null to chipError else amount to null
}
