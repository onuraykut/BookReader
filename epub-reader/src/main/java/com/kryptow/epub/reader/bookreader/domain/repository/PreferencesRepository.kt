package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getReadingPreferences(): Flow<ReadingPreferences>
    suspend fun updateReadingPreferences(preferences: ReadingPreferences)
}
