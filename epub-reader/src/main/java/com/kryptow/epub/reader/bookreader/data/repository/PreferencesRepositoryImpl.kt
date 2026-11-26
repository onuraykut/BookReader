package com.kryptow.epub.reader.bookreader.data.repository

import com.kryptow.epub.reader.bookreader.data.datastore.UserPreferencesDataStore
import com.kryptow.epub.reader.bookreader.domain.model.LibrarySortOrder
import com.kryptow.epub.reader.bookreader.domain.model.ReaderBehaviorPreferences
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepositoryImpl(
    private val dataStore: UserPreferencesDataStore,
) : PreferencesRepository {

    override fun getReadingPreferences(): Flow<ReadingPreferences> =
        dataStore.readingPreferences

    override suspend fun updateReadingPreferences(preferences: ReadingPreferences) =
        dataStore.updatePreferences(preferences)

    override fun getBehaviorPreferences(): Flow<ReaderBehaviorPreferences> =
        dataStore.behaviorPreferences

    override suspend fun updateBehaviorPreferences(preferences: ReaderBehaviorPreferences) =
        dataStore.updateBehaviorPreferences(preferences)

    override fun getLibrarySort(): Flow<LibrarySortOrder> =
        dataStore.librarySort.map { name ->
            LibrarySortOrder.entries.firstOrNull { it.name == name } ?: LibrarySortOrder.DEFAULT
        }

    override suspend fun setLibrarySort(order: LibrarySortOrder) =
        dataStore.setLibrarySort(order.name)
}
