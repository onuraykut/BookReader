package com.kryptow.epub.reader.bookreader.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: Long) = "reader/$bookId"
    }
    data object Settings : Screen("settings")
    data object Notes : Screen("notes/{bookId}") {
        fun createRoute(bookId: Long) = "notes/$bookId"
    }
}
