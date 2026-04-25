package com.kryptow.epub.reader.bookreader.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptow.epub.reader.bookreader.domain.model.ReadingPreferences
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<ReadingPreferences> = preferencesRepository
        .getReadingPreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingPreferences())

    fun updatePreferences(updated: ReadingPreferences) {
        viewModelScope.launch { preferencesRepository.updateReadingPreferences(updated) }
    }
}
