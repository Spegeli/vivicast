package com.vivicast.tv

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocaleHelper {
    private const val PREF_FILE = "locale_pref"
    private const val KEY_LANGUAGE = "language"

    fun applyLocale(context: Context): Context {
        val lang = getSavedLanguage(context)
        val locale = resolveLocale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun save(context: Context, language: String) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getSavedLanguage(context: Context): String =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "System") ?: "System"

    private fun resolveLocale(language: String): Locale = when (language) {
        "German" -> Locale("de")
        "English" -> Locale("en")
        else -> {
            val system = Resources.getSystem().configuration.locales[0].language
            if (system == "de") Locale("de") else Locale("en")
        }
    }
}
