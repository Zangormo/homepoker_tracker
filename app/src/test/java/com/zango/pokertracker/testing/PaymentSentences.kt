package com.zango.pokertracker.testing

import com.zango.pokertracker.domain.settlement.Payment
import com.zango.pokertracker.domain.settlement.Settlement

/**
 * Renders payments the way English renders them, for tests that are about *who pays whom*.
 *
 * The app itself names a string resource and lets the screen say it, which is what makes it
 * translatable — but a test asserting `UiText.of(R.string.settlement_pays, "Anna", "Boris",
 * "1.20")` is harder to read than the sentence it stands for, and the thing under test is the
 * matching, not the wording. That the sentence is built from the right resource and the right
 * arguments is asserted once, in `SettlementTextTest`.
 */
fun Payment.sentence(): String = "${from.name} pays ${to.name} ${amount.format()}"

fun Settlement.sentences(): List<String> = payments.map { it.sentence() }
