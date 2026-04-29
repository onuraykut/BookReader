package com.kryptow.epub.reader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import com.kryptow.epub.reader.bookreader.di.appModule
import com.kryptow.epub.reader.bookreader.di.databaseModule
import com.kryptow.epub.reader.bookreader.di.repositoryModule
import com.kryptow.epub.reader.bookreader.di.useCaseModule
import com.kryptow.epub.reader.bookreader.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * EpubReader kütüphanesinin giriş noktası.
 *
 * ## Kurulum
 *
 * `Application.onCreate()` içinde başlatın:
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         EpubReader.init(this)
 *     }
 * }
 * ```
 *
 * ## Kitap Açma
 *
 * Kitabı veritabanı ID'siyle açın:
 * ```kotlin
 * EpubReader.openBook(context, bookId = 42L)
 * ```
 *
 * Ya da doğrudan URI üzerinden:
 * ```kotlin
 * EpubReader.openUri(context, uri)
 * ```
 *
 * ## JitPack Bağımlılığı
 *
 * `settings.gradle.kts`:
 * ```kotlin
 * dependencyResolutionManagement {
 *     repositories {
 *         maven { url = uri("https://jitpack.io") }
 *     }
 * }
 * ```
 * `build.gradle.kts`:
 * ```kotlin
 * dependencies {
 *     implementation("com.github.kryptow:BookReader:TAG")
 * }
 * ```
 */
object EpubReader {

    private var initialized = false

    /**
     * Kütüphaneyi başlatır. Uygulama başlangıcında bir kez çağrılmalıdır.
     *
     * @param application      Android Application nesnesi.
     * @param extraKoinModules Uygulamaya özgü ek Koin modülleri (isteğe bağlı).
     */
    @JvmStatic
    fun init(application: Application, vararg extraKoinModules: Module) {
        if (initialized) return
        initialized = true

        // Koin zaten başlatılmışsa modülleri yükle; yoksa yeni bir context başlat.
        val existingKoin = GlobalContext.getOrNull()
        if (existingKoin != null) {
            existingKoin.loadModules(internalModules() + extraKoinModules.toList())
        } else {
            startKoin {
                androidContext(application)
                modules(internalModules() + extraKoinModules.toList())
            }
        }
    }

    /**
     * Kitabı DB kimliğiyle [EpubReaderActivity] içinde açar.
     *
     * @param context Android Context.
     * @param bookId  Kütüphane veritabanındaki kitap kimliği.
     */
    @JvmStatic
    fun openBook(context: Context, bookId: Long) {
        context.startActivity(
            Intent(context, EpubReaderActivity::class.java).apply {
                putExtra(EpubReaderActivity.EXTRA_BOOK_ID, bookId)
            },
        )
    }

    /**
     * Dosya yolundan kitabı açar.
     * Kitap daha önce eklenmemişse otomatik olarak veritabanına kaydedilir.
     *
     * @param context  Android Context.
     * @param filePath EPUB dosyasının tam yolu. Örn: `/storage/emulated/0/books/alice.epub`
     */
    @JvmStatic
    fun openFile(context: Context, filePath: String) {
        openUri(context, Uri.fromFile(File(filePath)))
    }

    /**
     * Content/File URI'sinden kitabı açar.
     * Kitap daha önce eklenmemişse otomatik olarak veritabanına kaydedilir.
     *
     * @param context Android Context.
     * @param uri     EPUB dosyasının URI'si (content:// veya file://).
     *
     * Örnek kullanım:
     * ```kotlin
     * // File seçici sonucu:
     * val uri: Uri = result.data?.data ?: return
     * EpubReader.openUri(context, uri)
     *
     * // Dosya yolu string'inden:
     * EpubReader.openFile(context, "/storage/emulated/0/book.epub")
     * ```
     */
    @JvmStatic
    fun openUri(context: Context, uri: Uri) {
        context.startActivity(
            Intent(context, EpubReaderActivity::class.java).apply {
                putExtra(EpubReaderActivity.EXTRA_FILE_URI, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    /**
     * Mevcut Koin modüllerini döndürür — kendi `startKoin` bloğunuza eklemek isterseniz.
     *
     * ```kotlin
     * startKoin {
     *     androidContext(this@App)
     *     modules(EpubReader.koinModules() + myModule)
     * }
     * ```
     */
    @JvmStatic
    fun koinModules(): List<Module> = internalModules()

    // ─── İç ────────────────────────────────────────────────────────────────────

    private fun internalModules(): List<Module> =
        listOf(appModule, databaseModule, repositoryModule, useCaseModule, viewModelModule)
}
