package com.zango.pokertracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zango.pokertracker.R
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.text.UiText
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.ui.theme.PokerTheme

/**
 * Chips and cash are different scales wearing the same digits, and mistaking one for the other is
 * this app's central hazard. Every number in the app therefore goes through one of the components
 * here, each of which carries an icon naming which of the two it is.
 */

private const val NO_VALUE = "—"

/** The minus glyph reads better than a hyphen at these sizes; display only, never in stored text. */
private fun String.typographicMinus() = replace('-', '−')

// ---------------------------------------------------------------------------------------------
// Input fields
// ---------------------------------------------------------------------------------------------

/**
 * A whole number of chips. The keyboard offers no decimal separator, because a fraction of a chip
 * does not exist; typing one anyway is caught and named by [error].
 */
@Composable
fun ChipAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: UiText? = null,
    enabled: Boolean = true,
    required: Boolean = false,
    forceShowError: Boolean = false,
    supporting: UiText? = null,
    imeAction: ImeAction = ImeAction.Next,
) = AmountField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    error = error,
    enabled = enabled,
    required = required,
    forceShowError = forceShowError,
    supporting = supporting,
    imeAction = imeAction,
    icon = PokerChip,
    iconDescription = stringResource(R.string.amount_chips_unit),
    iconTint = PokerTheme.colors.chip,
    keyboardType = KeyboardType.Number,
)

/** A cash amount. Parsing stays with `MoneyParser`; this only collects the text. */
@Composable
fun CashAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: UiText? = null,
    enabled: Boolean = true,
    required: Boolean = false,
    forceShowError: Boolean = false,
    supporting: UiText? = null,
    imeAction: ImeAction = ImeAction.Next,
) = AmountField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    error = error,
    enabled = enabled,
    required = required,
    forceShowError = forceShowError,
    supporting = supporting,
    imeAction = imeAction,
    icon = Icons.Filled.AttachMoney,
    iconDescription = stringResource(R.string.amount_cash_unit),
    iconTint = PokerTheme.colors.cash,
    keyboardType = KeyboardType.Decimal,
)

/** Plain text, sharing the same chrome and validation behaviour so forms stay consistent. */
@Composable
fun PokerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: UiText? = null,
    enabled: Boolean = true,
    required: Boolean = false,
    forceShowError: Boolean = false,
    supporting: UiText? = null,
    imeAction: ImeAction = ImeAction.Next,
    maxLength: Int? = null,
) = AmountField(
    value = value,
    // A field with a limit stops at it rather than letting a name grow too long and only then
    // complaining: the keystroke past the end simply does not arrive. Pasting something longer
    // keeps the part that fits, which is friendlier than dropping the paste on the floor.
    onValueChange = if (maxLength == null) {
        onValueChange
    } else {
        { text -> onValueChange(text.take(maxLength)) }
    },
    label = label,
    modifier = modifier,
    error = error,
    enabled = enabled,
    required = required,
    forceShowError = forceShowError,
    supporting = supporting,
    imeAction = imeAction,
    icon = null,
    iconDescription = null,
    iconTint = Color.Unspecified,
    keyboardType = KeyboardType.Text,
    textStyle = null,
)

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    error: UiText?,
    enabled: Boolean,
    required: Boolean,
    forceShowError: Boolean,
    supporting: UiText?,
    imeAction: ImeAction,
    icon: ImageVector?,
    iconDescription: String?,
    iconTint: Color,
    keyboardType: KeyboardType,
    textStyle: TextStyle? = PokerTheme.type.numericMedium,
) {
    // A fresh form must not open covered in red, so an untouched field stays neutral however
    // empty it is. Once a field has been visited and left, or the host has tried to submit, its
    // problems show and keep showing as they are corrected.
    var visited by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val showError = error != null && (touched || forceShowError)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused) visited = true else if (visited) touched = true
            },
        enabled = enabled,
        singleLine = true,
        isError = showError,
        textStyle = textStyle ?: LocalTextStyle.current,
        label = { Text(if (required) stringResource(R.string.field_required, label) else label) },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = iconDescription,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        supportingText = when {
            showError -> {
                { Text(error!!.resolve()) }
            }

            supporting != null -> {
                { Text(supporting.resolve()) }
            }

            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            // A resting outline is only 1.45:1 against a raised card, so the field is given a
            // container of its own; it stays findable even when the border barely registers.
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorLabelColor = MaterialTheme.colorScheme.error,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
            focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Read-only values
// ---------------------------------------------------------------------------------------------

@Composable
fun ChipAmountText(
    chips: Chips?,
    modifier: Modifier = Modifier,
    style: TextStyle = PokerTheme.type.numericSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showIcon: Boolean = true,
) = AmountText(
    text = chips?.count?.toString()?.typographicMinus() ?: NO_VALUE,
    semantic = chips?.let {
        stringResource(R.string.amount_spoken, it.count, stringResource(R.string.amount_chips_unit))
    } ?: stringResource(R.string.amount_no_chips),
    icon = PokerChip.takeIf { showIcon },
    iconTint = PokerTheme.colors.chip,
    style = style,
    color = if (chips == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
    modifier = modifier,
)

@Composable
fun CashAmountText(
    money: Money?,
    modifier: Modifier = Modifier,
    style: TextStyle = PokerTheme.type.numericSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showIcon: Boolean = true,
    signed: Boolean = false,
) = AmountText(
    text = money?.let { if (signed) it.formatSigned().typographicMinus() else it.format() }
        ?: NO_VALUE,
    semantic = money?.let {
        stringResource(R.string.amount_spoken, it.format(), stringResource(R.string.amount_cash_unit))
    } ?: stringResource(R.string.amount_no_cash),
    icon = Icons.Filled.AttachMoney.takeIf { showIcon },
    iconTint = PokerTheme.colors.cash,
    style = style,
    color = if (money == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
    modifier = modifier,
)

/**
 * Profit or loss. The sign is always printed, so the result survives being read by someone who
 * cannot separate the two colours, or a screenshot that has lost them.
 */
@Composable
fun NetCashText(
    net: Money?,
    modifier: Modifier = Modifier,
    style: TextStyle = PokerTheme.type.numericSmall,
    showIcon: Boolean = false,
) = CashAmountText(
    money = net,
    modifier = modifier,
    style = style,
    showIcon = showIcon,
    signed = true,
    color = when {
        net == null -> MaterialTheme.colorScheme.onSurfaceVariant
        net.isPositive -> PokerTheme.colors.positive
        net.isNegative -> PokerTheme.colors.negative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
)

@Composable
private fun AmountText(
    text: String,
    semantic: String,
    icon: ImageVector?,
    iconTint: Color,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    // Roughly cap height, floored so it survives small styles and capped so the hero total does
    // not end up with a dollar sign as tall as the number.
    val iconSize = with(LocalDensity.current) { style.fontSize.toDp() * 0.85f }
        .coerceIn(14.dp, 26.dp)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = semantic },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(text = text, style = style, color = color, textAlign = TextAlign.End)
    }
}

/**
 * A cash figure and the chips it buys, joined by an arrow so the pair reads as one conversion
 * rather than two unrelated numbers sitting next to each other.
 */
@Composable
fun CashToChipsRow(
    cash: Money?,
    chips: Chips?,
    modifier: Modifier = Modifier,
    style: TextStyle = PokerTheme.type.numericMedium,
) = ConversionRow(
    modifier = modifier,
    description = stringResource(
        R.string.amount_cash_to_chips,
        cash?.format() ?: NO_VALUE,
        chips?.count?.toString() ?: NO_VALUE,
    ),
    left = { CashAmountText(cash, style = style) },
    right = { ChipAmountText(chips, style = style) },
)

/** The same relationship read the other way, for counting a stack back into money. */
@Composable
fun ChipsToCashRow(
    chips: Chips?,
    cash: Money?,
    modifier: Modifier = Modifier,
    style: TextStyle = PokerTheme.type.numericMedium,
) = ConversionRow(
    modifier = modifier,
    description = stringResource(
        R.string.amount_chips_to_cash,
        chips?.count?.toString() ?: NO_VALUE,
        cash?.format() ?: NO_VALUE,
    ),
    left = { ChipAmountText(chips, style = style) },
    right = { CashAmountText(cash, style = style) },
)

@Composable
private fun ConversionRow(
    modifier: Modifier,
    description: String,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        left()
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        right()
    }
}
