package com.homepoker_tracker.ui.common

import com.homepoker_tracker.core.money.ChipConversion
import com.homepoker_tracker.core.money.ChipRate
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money

/**
 * A cash amount alongside what it is worth in chips, for the live "this is what you are setting
 * up" readouts. [leftOver] is non-null when the cash does not buy a whole number of chips.
 */
data class AmountPreview(
    val cash: Money? = null,
    val chips: Chips? = null,
    val leftOver: Money? = null,
) {
    val isEmpty: Boolean get() = cash == null

    companion object {
        fun of(cash: Money?, rate: ChipRate?): AmountPreview {
            if (cash == null) return AmountPreview()
            if (rate == null) return AmountPreview(cash = cash)
            return when (val conversion = rate.chipsFor(cash)) {
                is ChipConversion.Exact -> AmountPreview(cash, conversion.chips)
                is ChipConversion.Inexact ->
                    AmountPreview(cash, conversion.chips, conversion.remainder)
            }
        }
    }
}
