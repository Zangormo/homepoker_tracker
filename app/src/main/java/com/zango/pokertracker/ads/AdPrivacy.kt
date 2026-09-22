package com.zango.pokertracker.ads

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen needs of the ad consent: whether the user must be able to change their choice,
 * and a way to let them. Implemented by [ConsentManager]; kept apart, like the "Remove ads"
 * purchase, so a ViewModel can be tested without Google's consent SDK.
 */
interface AdPrivacy {

    /**
     * True where the law requires a way back into the consent choice (EEA, UK, Switzerland), as
     * Google's consent SDK reports it. Starts from the status saved on the last launch and follows
     * each refresh, so it can turn true a moment after a screen opens.
     */
    val isPrivacyOptionsRequired: StateFlow<Boolean>

    /** Shows Google's form for changing the consent choice, over [activity]. */
    fun showPrivacyOptionsForm(activity: Activity)
}
