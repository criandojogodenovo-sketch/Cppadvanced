package com.cppmobilestudio.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Preferências da app (tema, editor, autosave) — Fase A/M.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cppstudio_settings", Context.MODE_PRIVATE)

    var fontSizeSp: Int
        get() = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value.coerceIn(9, 32)).apply()

    var autosaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOSAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSAVE, value).apply()

    var highlightEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGHLIGHT, value).apply()

    /** "system" | "dark" | "light" — predefinição: escuro (Fase M). */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun isEditorDark(): Boolean = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> {
            // segue o sistema
            val ui = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ui != android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
    }

    private val context: Context = context.applicationContext

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_AUTOSAVE = "autosave"
        private const val KEY_HIGHLIGHT = "highlight"
        private const val KEY_THEME = "theme"
        const val DEFAULT_FONT = 14
        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }
}
