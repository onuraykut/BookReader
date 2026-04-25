package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.DictionaryEntry

interface DictionaryRepository {
    /** İngilizce sözlük tanımı döner (API + dosya önbelleği). */
    suspend fun getDefinition(word: String): DictionaryEntry?

    /** Kelimeyi [targetLang] diline çevirir (Google Translate + dosya önbelleği). */
    suspend fun translate(word: String, targetLang: String): String?
}
