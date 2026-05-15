package com.kryptow.epub.reader.bookreader.ui.navigation

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kryptow.epub.reader.bookreader.data.datastore.UserPreferencesDataStore
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import com.kryptow.epub.reader.bookreader.ui.screen.library.LibraryScreen
import com.kryptow.epub.reader.bookreader.ui.screen.notes.NotesScreen
import com.kryptow.epub.reader.bookreader.ui.screen.onboarding.OnboardingScreen
import com.kryptow.epub.reader.bookreader.ui.screen.pdf.PdfReaderScreen
import com.kryptow.epub.reader.bookreader.ui.screen.reader.ReaderScreen
import com.kryptow.epub.reader.bookreader.ui.screen.settings.SettingsScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavigation(navController: NavHostController) {
    val dataStore: UserPreferencesDataStore = koinInject()
    val onboardingShown by dataStore.onboardingShown.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // Wait until we know whether onboarding was shown (avoid flicker)
    val startDestination = when (onboardingShown) {
        null -> return // still loading, render nothing
        false -> Screen.Onboarding.route
        else -> Screen.Library.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    scope.launch { dataStore.markOnboardingShown() }
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            BookReaderRouter(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onNotesClick = { navController.navigate(Screen.Notes.createRoute(bookId)) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Notes.route,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            NotesScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Kitabın filePath uzantısına göre EPUB veya PDF ekranını açar.
 */
@Composable
private fun BookReaderRouter(
    bookId: Long,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit,
) {
    val bookRepository: BookRepository = koinInject()
    val context = LocalContext.current

    var filePath by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        filePath = bookRepository.getBookById(bookId)?.filePath
        loaded = true
    }

    if (!loaded) return

    val path = filePath ?: ""
    val uri = Uri.parse(path)

    if (uri.isPdf(context)) {
        val fileName = uri.getDisplayName(context)
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .ifBlank { "PDF Belgesi" }
        PdfReaderScreen(
            uri = uri,
            fileName = fileName,
            onBack = onBack,
        )
    } else {
        ReaderScreen(
            bookId = bookId,
            onBack = onBack,
            onSettingsClick = onSettingsClick,
            onNotesClick = onNotesClick,
        )
    }
}

// ─── Yardımcı ─────────────────────────────────────────────────────────────────

private fun Uri.isPdf(context: Context): Boolean {
    val mime = context.contentResolver.getType(this)?.lowercase()
    if (mime == "application/pdf") return true
    val name = getDisplayName(context).lowercase()
    if (name.endsWith(".pdf")) return true
    return toString().lowercase().let { it.endsWith(".pdf") || it.contains(".pdf?") }
}

private fun Uri.getDisplayName(context: Context): String {
    var name: String? = null
    runCatching {
        val cursor: Cursor? = context.contentResolver.query(
            this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
    }
    return name ?: lastPathSegment ?: toString()
}
