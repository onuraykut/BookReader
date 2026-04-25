package com.kryptow.epub.reader.bookreader.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kryptow.epub.reader.bookreader.domain.model.DayTheme
import com.kryptow.epub.reader.bookreader.domain.model.NightTheme
import com.kryptow.epub.reader.bookreader.domain.model.ReadingFontFamily
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.model.ScrollMode
import com.kryptow.epub.reader.bookreader.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_preferences")

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val MARGIN_HORIZONTAL = intPreferencesKey("margin_horizontal")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAY_THEME = stringPreferencesKey("day_theme")
        val NIGHT_THEME = stringPreferencesKey("night_theme")
        val AUTO_NIGHT_START = intPreferencesKey("auto_night_start")
        val AUTO_NIGHT_END = intPreferencesKey("auto_night_end")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
    }

    val readingPreferences: Flow<ReadingPreferences> = context.dataStore.data.map { p ->
        ReadingPreferences(
            fontSize = p[Keys.FONT_SIZE] ?: 18f,
            lineHeight = p[Keys.LINE_HEIGHT] ?: 1.5f,
            fontFamily = enumOrDefault(p[Keys.FONT_FAMILY], ReadingFontFamily.SERIF),
            marginHorizontal = p[Keys.MARGIN_HORIZONTAL] ?: 24,
            themeMode = enumOrDefault(p[Keys.THEME_MODE], ThemeMode.DAY),
            dayTheme = enumOrDefault(p[Keys.DAY_THEME], DayTheme.WHITE),
            nightTheme = enumOrDefault(p[Keys.NIGHT_THEME], NightTheme.DARK_GRAY),
            autoNightStartHour = p[Keys.AUTO_NIGHT_START] ?: 20,
            autoNightEndHour = p[Keys.AUTO_NIGHT_END] ?: 7,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            scrollMode = enumOrDefault(p[Keys.SCROLL_MODE], ScrollMode.VERTICAL),
        )
    }

    suspend fun updatePreferences(prefs: ReadingPreferences) {
        context.dataStore.edit { p ->
            p[Keys.FONT_SIZE] = prefs.fontSize
            p[Keys.LINE_HEIGHT] = prefs.lineHeight
            p[Keys.FONT_FAMILY] = prefs.fontFamily.name
            p[Keys.MARGIN_HORIZONTAL] = prefs.marginHorizontal
            p[Keys.THEME_MODE] = prefs.themeMode.name
            p[Keys.DAY_THEME] = prefs.dayTheme.name
            p[Keys.NIGHT_THEME] = prefs.nightTheme.name
            p[Keys.AUTO_NIGHT_START] = prefs.autoNightStartHour
            p[Keys.AUTO_NIGHT_END] = prefs.autoNightEndHour
            p[Keys.KEEP_SCREEN_ON] = prefs.keepScreenOn
            p[Keys.SCROLL_MODE] = prefs.scrollMode.name
        }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        runCatching { name?.let { enumValueOf<E>(it) } }.getOrNull() ?: default
}
