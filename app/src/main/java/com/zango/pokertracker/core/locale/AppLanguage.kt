package com.zango.pokertracker.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.zango.pokertracker.R
import java.util.Locale

/**
 * The languages the app ships in.
 *
 * The label is written in its own language rather than translated, so someone who has landed in
 * a language they cannot read can still find their way back.
 */
enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    ENGLISH("en", R.string.language_english),
    RUSSIAN("ru", R.string.language_russian),
    ;

    companion object {
        /** The language for a tag such as "ru-RU", or null if the app has no translation for it. */
        fun forTag(tag: String?): AppLanguage? =
            tag?.substringBefore('-')?.let { base -> entries.firstOrNull { it.tag == base } }
    }
}

/**
 * Where the chosen language lives, and how it gets applied.
 *
 * Android 13 gained a per-app language setting that the system stores and shows in its own
 * settings app; below that there is nothing to store it in, so the choice is kept in preferences
 * and applied by wrapping the activity's context as it is created. The preference is written on
 * every platform regardless, so one place always knows the answer — including
 * `attachBaseContext`, which runs long before anything is injected.
 */
object AppLanguageStore {

    private const val PREFERENCES = "settings"
    private const val KEY_LANGUAGE = "language"

    /** The language in force: the host's choice, or the closest match to the device's own. */
    fun current(context: Context): AppLanguage =
        stored(context) ?: AppLanguage.forTag(deviceTag()) ?: AppLanguage.ENGLISH

    fun stored(context: Context): AppLanguage? = AppLanguage.forTag(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null),
    )

    /**
     * Records the choice and asks the platform to apply it.
     *
     * On Android 13 and later the system owns per-app languages: handing it the locale is what
     * makes the app appear in the system language picker, and it recreates the activity itself.
     * Below that, the caller recreates the activity and [wrap] applies the locale as it is built.
     */
    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE, language.tag) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
        }
    }

    /**
     * A context that resolves resources in the stored language.
     *
     * Only does anything below Android 13; above it the platform has already applied the locale
     * before the activity is built, and overriding it again would fight the system.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = stored(base) ?: return base
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun deviceTag(): String? =
        LocaleList.getDefault().takeIf { !it.isEmpty }?.get(0)?.language
}

/** The activity behind a Composable's context, so a language change can restart it. */
fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
