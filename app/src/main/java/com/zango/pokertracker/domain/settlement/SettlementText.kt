package com.zango.pokertracker.domain.settlement

import com.zango.pokertracker.R
import com.zango.pokertracker.core.text.UiText

/**
 * A settlement as lines of text, for the share and copy buttons.
 *
 * Deliberately unadorned: this gets pasted into a group chat and read by people who were not
 * looking at the app, so every line has to stand on its own without a legend.
 *
 * The lines are named rather than written, because this runs in the domain and has no way to know
 * what language the phone is in. The screen resolves them and joins them with newlines, which is
 * also why this returns the parts rather than a finished block of text.
 */
fun Settlement.shareLines(gameName: String): List<UiText> = buildList {
    add(UiText.of(R.string.settlement_share_subject, gameName))
    add(BLANK)

    if (payments.isEmpty()) {
        add(UiText.of(R.string.settlement_everyone_even))
    } else {
        payments.forEach { add(it.toSentence()) }
    }

    val notes = notes()
    if (notes.isNotEmpty()) {
        add(BLANK)
        addAll(notes)
    }
}

/** "Anna pays Boris 4.50" — a whole instruction in one line, no interpretation required. */
fun Payment.toSentence(): UiText =
    UiText.of(R.string.settlement_pays, from.name, to.name, amount.format())

/**
 * Caveats the host should see alongside the payments: money nudged by rounding, and any
 * discrepancy carried over from a chip count that did not reconcile.
 */
fun Settlement.notes(): List<UiText> = buildList {
    if (hasRoundingAdjustment && adjustedPlayer != null) {
        add(
            UiText.of(
                R.string.note_rounded,
                roundingUnit.format(),
                adjustedPlayer.name,
                roundingAdjustment.abs().format(),
            ),
        )
    }
    if (!isBalanced) {
        add(
            UiText.of(
                R.string.note_imbalance,
                imbalance.abs().format(),
                UiText.of(
                    if (imbalance.isNegative) R.string.note_imbalance_short
                    else R.string.note_imbalance_over,
                ),
            ),
        )
    }
    unsettled.forEach {
        add(
            UiText.of(
                if (it.net.isPositive) R.string.note_still_owed else R.string.note_still_owes,
                it.player.name,
                it.net.abs().format(),
            ),
        )
    }
}

/** A blank line between the instructions and the small print. */
private val BLANK = UiText.Raw("")
