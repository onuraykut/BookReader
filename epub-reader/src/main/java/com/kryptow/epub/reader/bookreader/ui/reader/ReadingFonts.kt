package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.ui.text.font.FontFamily
import com.kryptow.epub.reader.bookreader.domain.model.ReadingFontFamily

/** ReadingFontFamily → Compose FontFamily */
fun ReadingFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReadingFontFamily.SERIF -> FontFamily.Serif
    ReadingFontFamily.SANS_SERIF -> FontFamily.SansSerif
    ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
    ReadingFontFamily.CURSIVE -> FontFamily.Cursive
    ReadingFontFamily.SYSTEM -> FontFamily.Default
}

/** ReadingFontFamily → CSS font-family (WebView için) */
fun ReadingFontFamily.toCssFontFamily(): String = when (this) {
    ReadingFontFamily.SERIF -> "Georgia, 'Times New Roman', serif"
    ReadingFontFamily.SANS_SERIF -> "'Helvetica Neue', Helvetica, Arial, sans-serif"
    ReadingFontFamily.MONOSPACE -> "'Courier New', Courier, monospace"
    ReadingFontFamily.CURSIVE -> "cursive"
    ReadingFontFamily.SYSTEM -> "system-ui, sans-serif"
}

/** Türkçe kullanıcı etiketi */
fun ReadingFontFamily.label(): String = when (this) {
    ReadingFontFamily.SERIF -> "Serif"
    ReadingFontFamily.SANS_SERIF -> "Sans-serif"
    ReadingFontFamily.MONOSPACE -> "Monospace"
    ReadingFontFamily.CURSIVE -> "Cursive"
    ReadingFontFamily.SYSTEM -> "Sistem"
}
