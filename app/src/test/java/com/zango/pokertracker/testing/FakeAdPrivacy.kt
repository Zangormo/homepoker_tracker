package com.zango.pokertracker.testing

import android.app.Activity
import com.zango.pokertracker.ads.AdPrivacy
import kotlinx.coroutines.flow.MutableStateFlow

/** Google's consent SDK stood in for: the requirement is set directly, and forms are counted. */
class FakeAdPrivacy : AdPrivacy {
    override val isPrivacyOptionsRequired = MutableStateFlow(false)

    var formsShown = 0
        private set

    override fun showPrivacyOptionsForm(activity: Activity) {
        formsShown++
    }
}
