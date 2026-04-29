package com.kryptow.epub.reader.bookreader.domain.model

data class ReadingPreferences(
    // Tipografi
    val fontSize: Float = 18f,                      // 12pt - 32pt
    val lineHeight: Float = 1.5f,                   // 1.0x - 2.0x
    val fontFamily: ReadingFontFamily = ReadingFontFamily.SERIF,
    val marginHorizontal: Int = 24,                 // dp

    // Tema
    val themeMode: ThemeMode = ThemeMode.DAY,
    val dayTheme: DayTheme = DayTheme.WHITE,
    val nightTheme: NightTheme = NightTheme.DARK_GRAY,
    val autoNightStartHour: Int = 20,               // 20:00
    val autoNightEndHour: Int = 7,                  // 07:00

    // Davranış
    val keepScreenOn: Boolean = true,
    val scrollMode: ScrollMode = ScrollMode.HORIZONTAL_PAGE,
) {
    /** Şu anki saate göre gece modu aktif mi? */
    fun shouldUseNightMode(currentHour: Int): Boolean = when (themeMode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.AUTO -> {
            val start = autoNightStartHour
            val end = autoNightEndHour
            if (start > end) currentHour >= start || currentHour < end
            else currentHour in start..end
        }
    }
}

enum class ThemeMode { DAY, NIGHT, AUTO }

enum class DayTheme { WHITE, CREAM, LIGHT_BLUE }

enum class NightTheme { TRUE_BLACK, DARK_GRAY, SEPIA }

enum class ReadingFontFamily { SERIF, SANS_SERIF, MONOSPACE, SYSTEM, CURSIVE }

enum class ScrollMode { VERTICAL, HORIZONTAL_PAGE }
