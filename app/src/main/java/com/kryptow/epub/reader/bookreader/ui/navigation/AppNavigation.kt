package com.kryptow.epub.reader.bookreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kryptow.epub.reader.bookreader.ui.screen.library.LibraryScreen
import com.kryptow.epub.reader.bookreader.ui.screen.notes.NotesScreen
import com.kryptow.epub.reader.bookreader.ui.screen.reader.ReaderScreen
import com.kryptow.epub.reader.bookreader.ui.screen.settings.SettingsScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
    ) {
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
            ReaderScreen(
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
