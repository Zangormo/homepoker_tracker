package com.zango.pokertracker.ui.donation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zango.pokertracker.R
import com.zango.pokertracker.ads.InterstitialAdController
import com.zango.pokertracker.billing.RemoveAdsBilling
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DonationPromptViewModel @Inject constructor(
    billing: RemoveAdsBilling,
    interstitialAds: InterstitialAdController,
) : ViewModel() {
    /** Someone who has already paid is never asked again. */
    val isAdsRemoved: StateFlow<Boolean> = billing.isAdsRemoved

    /** The end-of-game ad, if one is on screen: the prompt waits for it to be closed. */
    val isShowingAd: StateFlow<Boolean> = interstitialAds.isShowingAd
}

/**
 * Asks for a small donation once a game is over, the one moment the host is not busy.
 *
 * The donation is the "Remove ads" purchase, so it is only offered while ads are still on, and
 * "Donate now" takes the host to it in settings rather than opening a payment sheet unannounced.
 * [offered] is read once: after either button, the prompt stays closed for this settlement even if
 * the screen is rebuilt.
 */
@Composable
fun DonationPrompt(
    offered: Boolean,
    onDonate: () -> Unit,
    viewModel: DonationPromptViewModel = hiltViewModel(),
) {
    var open by rememberSaveable { mutableStateOf(offered) }
    // Whether an ad played on the way in. Remembered, because by the time the prompt shows the
    // ad has already gone.
    var afterAd by rememberSaveable { mutableStateOf(false) }
    val adsRemoved by viewModel.isAdsRemoved.collectAsStateWithLifecycle()
    val showingAd by viewModel.isShowingAd.collectAsStateWithLifecycle()
    LaunchedEffect(showingAd) { if (showingAd) afterAd = true }
    // Held back while the ad is up, so it opens the moment the ad closes rather than behind it.
    if (!open || adsRemoved || showingAd) return

    DonationDialog(
        afterAd = afterAd,
        onDismiss = { open = false },
        onDonate = {
            open = false
            onDonate()
        },
    )
}

@Composable
private fun DonationDialog(afterAd: Boolean, onDismiss: () -> Unit, onDonate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                stringResource(if (afterAd) R.string.donation_title_after_ad else R.string.donation_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.donation_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        if (afterAd) R.string.donation_ads_note_after_ad else R.string.donation_ads_note,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDonate) { Text(stringResource(R.string.donation_donate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.donation_no_thanks)) }
        },
    )
}

@Preview(name = "Donation prompt", showBackground = true, heightDp = 520)
@Composable
private fun DonationDialogPreview() {
    PokerTrackerTheme {
        DonationDialog(afterAd = false, onDismiss = {}, onDonate = {})
    }
}

@Preview(name = "Donation prompt — after an ad", showBackground = true, heightDp = 520)
@Composable
private fun DonationDialogAfterAdPreview() {
    PokerTrackerTheme {
        DonationDialog(afterAd = true, onDismiss = {}, onDonate = {})
    }
}
