package com.kryptow.epub.reader.bookreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.kryptow.epub.reader.EpubReader
import com.kryptow.epub.reader.bookreader.ui.navigation.AppNavigation
import com.kryptow.epub.reader.bookreader.ui.theme.BookReaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen super.onCreate'den ÖNCE çağrılmalı
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Dışarıdan gelen VIEW intent'i (dosya tıklama vs.)
        handleViewIntent(intent)

        setContent {
            BookReaderTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }

    /**
     * Uygulama açıkken yeni bir VIEW intent gelirse (örn. çoklu dosya seçiminde).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Sistem dosya seçicisinden / file manager'dan gelen EPUB veya PDF URI'sini
     * EpubReader üzerinden aç. EpubReaderActivity dosyayı otomatik olarak DB'ye ekler.
     */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return

        // URI'yi okuma izniyle aç
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        EpubReader.openUri(this, uri)
    }
}
