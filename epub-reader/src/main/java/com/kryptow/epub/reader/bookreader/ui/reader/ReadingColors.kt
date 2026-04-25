package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.ui.graphics.Color
import com.kryptow.epub.reader.bookreader.domain.model.DayTheme
import com.kryptow.epub.reader.bookreader.domain.model.NightTheme
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import java.util.Calendar

data class ReadingColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val isDark: Boolean,
) {
    /** CSS renk stringlerine dönüştür (WebView için) */
    fun toCss(): CssColors = CssColors(
        background = background.toHex(),
        text = text.toHex(),
        textSecondary = textSecondary.toHex(),
        accent = accent.toHex(),
    )
}

data class CssColors(
    val background: String,
    val text: String,
    val textSecondary: String,
    val accent: String,
)

fun ReadingPreferences.resolveColors(currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): ReadingColors =
    if (shouldUseNightMode(currentHour)) nightTheme.colors() else dayTheme.colors()

fun DayTheme.colors(): ReadingColors = when (this) {
    DayTheme.WHITE -> ReadingColors(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF5F5F5),
        text = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF555555),
        accent = Color(0xFF1565C0),
        isDark = false,
    )
    DayTheme.CREAM -> ReadingColors(
        background = Color(0xFFFBF0D9),
        surface = Color(0xFFF3E4BE),
        text = Color(0xFF3B2F1E),
        textSecondary = Color(0xFF6B5A3F),
        accent = Color(0xFF8B5A2B),
        isDark = false,
    )
    DayTheme.LIGHT_BLUE -> ReadingColors(
        background = Color(0xFFE3F2FD),
        surface = Color(0xFFBBDEFB),
        text = Color(0xFF0D2A42),
        textSecondary = Color(0xFF355A7E),
        accent = Color(0xFF1976D2),
        isDark = false,
    )
}

fun NightTheme.colors(): ReadingColors = when (this) {
    NightTheme.TRUE_BLACK -> ReadingColors(
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        text = Color(0xFFE8E8E8),
        textSecondary = Color(0xFFA0A0A0),
        accent = Color(0xFF64B5F6),
        isDark = true,
    )
    NightTheme.DARK_GRAY -> ReadingColors(
        background = Color(0xFF1E1E1E),
        surface = Color(0xFF2A2A2A),
        text = Color(0xFFE0E0E0),
        textSecondary = Color(0xFF9E9E9E),
        accent = Color(0xFF64B5F6),
        isDark = true,
    )
    NightTheme.SEPIA -> ReadingColors(
        background = Color(0xFF2B2417),
        surface = Color(0xFF3A3022),
        text = Color(0xFFD4C4A0),
        textSecondary = Color(0xFFAD9E7A),
        accent = Color(0xFFD4A574),
        isDark = true,
    )
}

private fun Color.toHex(): String {
    val argb = this.value.toLong().shr(32).toInt()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}
