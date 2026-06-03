package com.examhub.student.service

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguagePreferenceManager {
    private const val PREFS_NAME = "language_preferences"
    private const val KEY_LANGUAGE = "language"
    const val LANGUAGE_VI = "vi"
    const val LANGUAGE_EN = "en"

    fun applySavedLanguage(context: Context) {
        setAppLanguage(languageTag(context))
    }

    fun setLanguage(context: Context, languageTag: String) {
        val normalized = normalize(languageTag)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
        setAppLanguage(normalized)
    }

    fun languageTag(context: Context): String {
        return normalize(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANGUAGE_VI)
        )
    }

    private fun setAppLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalize(languageTag))
        )
    }

    private fun normalize(languageTag: String?): String {
        return when (languageTag?.lowercase()) {
            LANGUAGE_EN -> LANGUAGE_EN
            else -> LANGUAGE_VI
        }
    }
}
