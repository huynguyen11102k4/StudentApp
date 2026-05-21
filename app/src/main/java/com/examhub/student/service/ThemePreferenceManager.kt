package com.examhub.student.service

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemePreferenceManager {
    private const val PREFS_NAME = "theme_preferences"
    private const val KEY_NIGHT_MODE = "night_mode"

    fun applySavedMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getSavedMode(context))
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        val mode = if (enabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NIGHT_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkModeApplied(context: Context): Boolean {
        return getSavedMode(context) == AppCompatDelegate.MODE_NIGHT_YES
    }

    private fun getSavedMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO)
        return if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            prefs.edit()
                .putInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO)
                .apply()
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            mode
        }
    }
}
