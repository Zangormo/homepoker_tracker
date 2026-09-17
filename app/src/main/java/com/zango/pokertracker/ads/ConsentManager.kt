package com.zango.pokertracker.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.zango.pokertracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single gate between the app and the Google Mobile Ads SDK.
 *
 * Nothing ad-related may run before this class says so. The order is the one Google's UMP guide
 * lays down: ask UMP for the current consent status, show its form if the user is in a region that
 * needs one (EEA, UK, Switzerland), and only once [ConsentInformation.canRequestAds] is true call
 * [MobileAds.initialize]. [adsReady] turns true after that call, and every banner and interstitial
 * waits on it - so no ad request can go out ahead of consent.
 *
 * Outside regulated regions UMP reports that ads may be requested straight after the update, with
 * no form shown, and the same path initializes the SDK.
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consentInformation: ConsentInformation,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializeCalled = AtomicBoolean(false)

    private val _adsReady = MutableStateFlow(false)

    /** True once consent allows ads and the Mobile Ads SDK has been initialized. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    /** Whether ads may be requested right now, as UMP last reported it. */
    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    /**
     * Whether the user must be offered a way to change their choice later. When true, a "Privacy
     * options" entry calling [showPrivacyOptionsForm] has to be reachable in the app.
     */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Refreshes consent and shows the form if one is required. Called from every activity
     * creation, as UMP asks for an update on each launch; a form is only ever shown while the
     * status actually calls for one.
     */
    fun gatherConsent(activity: Activity) {
        // Consent stored in an earlier session is still valid while the refresh runs, so a
        // returning user's ads need not wait on the network round trip.
        initializeIfAllowed()

        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters(activity),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let(::logError)
                    initializeIfAllowed()
                }
            },
            { requestError ->
                // Offline, most likely. Whatever was stored before still decides.
                logError(requestError)
                initializeIfAllowed()
            },
        )
    }

    /** Lets the user revisit their choice. Only meaningful while [isPrivacyOptionsRequired]. */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let(::logError)
            onDismissed(formError)
        }
    }

    private fun initializeIfAllowed() {
        if (!consentInformation.canRequestAds()) return
        if (initializeCalled.getAndSet(true)) return
        // Google's guide initializes on a background thread; it does disk and network work.
        scope.launch {
            MobileAds.initialize(context) {}
            _adsReady.value = true
        }
    }

    private fun requestParameters(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        val testDevice = BuildConfig.UMP_TEST_DEVICE_HASHED_ID
        if (BuildConfig.DEBUG && testDevice.isNotEmpty()) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .addTestDeviceHashedId(testDevice)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun logError(error: FormError) {
        Log.w(TAG, "UMP error ${error.errorCode}: ${error.message}")
    }

    private companion object {
        const val TAG = "ConsentManager"
    }
}
