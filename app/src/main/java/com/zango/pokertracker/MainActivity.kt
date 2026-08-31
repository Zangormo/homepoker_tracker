package com.zango.pokertracker

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zango.pokertracker.core.locale.AppLanguageStore
import com.zango.pokertracker.ui.navigation.PokerNavHost
import com.zango.pokertracker.ui.theme.PokerTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Applies the chosen language before any resource is read. Below Android 13 there is no
     * platform per-app language, so the locale has to be put onto the context by hand, and this
     * is the last moment before the first `getString` runs.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app has no light variant, so the system bars are pinned to light icons rather than
        // following the device theme, which would put dark icons on a near-black background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            PokerTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PokerNavHost()
                }
            }
        }
    }
}
