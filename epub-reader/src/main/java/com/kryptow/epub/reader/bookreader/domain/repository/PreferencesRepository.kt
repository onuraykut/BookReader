package com.kryptow.epub.reader.bookreader.domain.repository

import com.kryptow.epub.reader.bookreader.domain.model.LibrarySortOrder
import com.kryptow.epub.reader.bookreader.domain.model.ReaderBehaviorPreferences
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getReadingPreferences(): Flow<ReadingPreferences>
    suspend fun updateReadingPreferences(preferences: ReadingPreferences)

    fun getBehaviorPreferences(): Flow<ReaderBehaviorPreferences>
    suspend fun updateBehaviorPreferences(preferences: ReaderBehaviorPreferences)

    fun getLibrarySort(): Flow<LibrarySortOrder>
    suspend fun setLibrarySort(order: LibrarySortOrder)
}
