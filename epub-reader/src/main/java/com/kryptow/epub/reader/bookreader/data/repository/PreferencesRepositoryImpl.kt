package com.kryptow.epub.reader.bookreader.data.repository

import com.kryptow.epub.reader.bookreader.data.datastore.UserPreferencesDataStore
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class PreferencesRepositoryImpl(
    private val dataStore: UserPreferencesDataStore,
) : PreferencesRepository {

    override fun getReadingPreferences(): Flow<ReadingPreferences> =
        dataStore.readingPreferences

    override suspend fun updateReadingPreferences(preferences: ReadingPreferences) =
        dataStore.updatePreferences(preferences)
}
