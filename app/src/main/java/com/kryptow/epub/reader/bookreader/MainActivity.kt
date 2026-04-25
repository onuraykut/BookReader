package com.kryptow.epub.reader.bookreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.kryptow.epub.reader.bookreader.ui.navigation.AppNavigation
import com.kryptow.epub.reader.bookreader.ui.theme.BookReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookReaderTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
