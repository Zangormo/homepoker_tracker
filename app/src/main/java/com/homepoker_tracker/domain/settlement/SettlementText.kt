package com.homepoker_tracker.domain.settlement

/**
 * Renders a settlement as plain text for the share/copy button.
 *
 * Deliberately unadorned: this gets pasted into a group chat and read by people who were not
 * looking at the app, so every line has to stand on its own without a legend.
 */
fun Settlement.toShareText(gameName: String): String = buildString {
    appendLine("$gameName — settlement")
    appendLine()

    if (payments.isEmpty()) {
        appendLine("Everyone broke even. No payments needed.")
    } else {
        payments.forEach { appendLine(it.toSentence()) }
    }

    val notes = notes()
    if (notes.isNotEmpty()) {
        appendLine()
        notes.forEach { appendLine(it) }
    }
}.trimEnd()

/** "Anna pays Boris 4.50" — a whole instruction in one line, no interpretation required. */
fun Payment.toSentence(): String = "${from.name} pays ${to.name} ${amount.format()}"

/**
 * Caveats the host should see alongside the payments: money nudged by rounding, and any
 * discrepancy carried over from a chip count that did not reconcile.
 */
fun Settlement.notes(): List<String> = buildList {
    if (hasRoundingAdjustment && adjustedPlayer != null) {
        add(
            "Rounded to the nearest ${roundingUnit.format()}. " +
                "${adjustedPlayer.name} absorbed ${roundingAdjustment.abs().format()}.",
        )
    }
    if (!isBalanced) {
        val direction = if (imbalance.isNegative) "short of" else "over"
        add(
            "Chip counts came out ${imbalance.abs().format()} $direction the buy-ins, " +
                "so these payments do not fully square everyone up.",
        )
    }
    unsettled.forEach {
        val verb = if (it.net.isPositive) "is still owed" else "still owes"
        add("${it.player.name} $verb ${it.net.abs().format()}.")
    }
}
