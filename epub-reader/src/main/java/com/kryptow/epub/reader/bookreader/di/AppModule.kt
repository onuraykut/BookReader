package com.kryptow.epub.reader.bookreader.di

import androidx.room.Room
import com.kryptow.epub.reader.bookreader.data.database.BookDatabase
import com.kryptow.epub.reader.bookreader.data.datastore.UserPreferencesDataStore
import com.kryptow.epub.reader.bookreader.data.repository.BookRepositoryImpl
import com.kryptow.epub.reader.bookreader.data.repository.DictionaryRepositoryImpl
import com.kryptow.epub.reader.bookreader.data.repository.HighlightRepositoryImpl
import com.kryptow.epub.reader.bookreader.data.repository.PreferencesRepositoryImpl
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.domain.repository.DictionaryRepository
import com.kryptow.epub.reader.bookreader.domain.repository.HighlightRepository
import com.kryptow.epub.reader.bookreader.domain.repository.PreferencesRepository
import com.kryptow.epub.reader.bookreader.domain.usecase.AddBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.AddHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.DeleteBookUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.DeleteHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBookmarksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetFavoriteBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetHighlightsUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetPageHighlightsUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.GetRecentBooksUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.ToggleFavoriteUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.UpdateHighlightUseCase
import com.kryptow.epub.reader.bookreader.domain.usecase.UpdateReadingProgressUseCase
import com.kryptow.epub.reader.bookreader.epub.EpubParser
import com.kryptow.epub.reader.bookreader.epub.FolderScanner
import com.kryptow.epub.reader.bookreader.ui.reader.TtsManager
import com.kryptow.epub.reader.bookreader.ui.screen.library.LibraryViewModel
import com.kryptow.epub.reader.bookreader.ui.screen.notes.NotesViewModel
import com.kryptow.epub.reader.bookreader.ui.screen.reader.ReaderViewModel
import com.kryptow.epub.reader.bookreader.ui.screen.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { EpubParser(androidContext()) }
    single { FolderScanner(androidContext()) }
    single { UserPreferencesDataStore(androidContext()) }
    // Her ReaderViewModel kendi TtsManager örneğini alır (factory)
    factory { TtsManager(androidContext()) }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            BookDatabase::class.java,
            BookDatabase.DATABASE_NAME,
        )
            .addMigrations(BookDatabase.MIGRATION_1_2, BookDatabase.MIGRATION_2_3)
            .build()
    }
    single { get<BookDatabase>().bookDao() }
    single { get<BookDatabase>().bookmarkDao() }
    single { get<BookDatabase>().bookProgressDao() }
    single { get<BookDatabase>().highlightDao() }
}

val repositoryModule = module {
    single<BookRepository> { BookRepositoryImpl(get(), get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(get()) }
    single<HighlightRepository> { HighlightRepositoryImpl(get()) }
    single<DictionaryRepository> { DictionaryRepositoryImpl(androidContext()) }
}

val useCaseModule = module {
    factory { GetBooksUseCase(get()) }
    factory { GetRecentBooksUseCase(get()) }
    factory { GetFavoriteBooksUseCase(get()) }
    factory { AddBookUseCase(get()) }
    factory { DeleteBookUseCase(get()) }
    factory { UpdateReadingProgressUseCase(get()) }
    factory { GetBookmarksUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { GetHighlightsUseCase(get()) }
    factory { GetPageHighlightsUseCase(get()) }
    factory { AddHighlightUseCase(get()) }
    factory { UpdateHighlightUseCase(get()) }
    factory { DeleteHighlightUseCase(get()) }
}

val viewModelModule = module {
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { (bookId: Long) -> NotesViewModel(bookId, get(), get(), get()) }
}
