package com.zango.pokertracker.ui.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.zango.pokertracker.ads.ConsentManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Reaches the consent gate from a leaf composable, which has no ViewModel of its own. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdsEntryPoint {
    fun consentManager(): ConsentManager
}

/**
 * An anchored adaptive banner for the bottom of a screen.
 *
 * Takes no space at all until an ad has actually loaded, so a user who declined consent, is
 * offline, or has no fill sees the screen exactly as it was before ads existed. Nothing is
 * requested until [ConsentManager.adsReady] is true. Once shown, it pads itself clear of the
 * navigation bar, since the app draws edge to edge and an ad must never sit under system UI.
 */
@Composable
fun BannerAd(adUnitId: String, modifier: Modifier = Modifier) {
    // Previews have no Hilt graph and no ads SDK.
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val consentManager = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AdsEntryPoint::class.java)
            .consentManager()
    }
    val adsReady by consentManager.adsReady.collectAsStateWithLifecycle()
    if (!adsReady) return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        // A new width (rotation, split screen) needs a banner sized for it, not a stretched one.
        key(adUnitId, widthDp) {
            var loaded by remember { mutableStateOf(false) }
            var adView by remember { mutableStateOf<AdView?>(null) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> adView?.resume()
                        Lifecycle.Event.ON_PAUSE -> adView?.pause()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AndroidView(
                modifier = if (loaded) {
                    Modifier.fillMaxWidth().navigationBarsPadding()
                } else {
                    Modifier.fillMaxWidth().height(0.dp)
                },
                factory = { viewContext ->
                    AdView(viewContext).apply {
                        this.adUnitId = adUnitId
                        // Google's guide now uses getLargeAnchoredAdaptiveBannerAdSize, which only
                        // exists from SDK 25; this is its 24.x equivalent.
                        setAdSize(
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(viewContext, widthDp),
                        )
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                loaded = true
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                loaded = false
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                        adView = this
                    }
                },
                onRelease = { view ->
                    adView = null
                    view.destroy()
                },
            )
        }
    }
}
