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
     * Java / Koin kullanmayan uygulamalar için basit başlatma metodu.
     * Uygulama başlangıcında bir kez çağrılmalıdır.
     */
    @JvmStatic
    fun init(application: Application) = init(application, *emptyArray<Module>())

    /**
     * Koin kullanan Kotlin uygulamaları için — ek modül geçilebilir.
     * Java tarafından görünmez; Java için parametresiz [init] kullanın.
     */
    @JvmSynthetic
    fun init(application: Application, vararg extraKoinModules: Module) {
        if (initialized) return
        initialized = true

        // PDFBox kaynak yükleyici — PDF metin çıkarma (arama) için gerekli
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(application.applicationContext)

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
     * Eski (legacy) bir okuyucudan geçiş yapan uygulamalar için opsiyonel başlangıç
     * pozisyonu da geçilebilir — bkz. [legacyPageNumber].
     *
     * **Geriye uyumluluk:** `openFile(context, filePath)` çağrısı default'larla bu metoda
     * delegate olur, davranışı değişmez.
     *
     * @param context             Android Context.
     * @param filePath            EPUB dosyasının tam yolu. Örn: `/storage/emulated/0/books/alice.epub`
     * @param initialChapter      Opsiyonel: başlangıç bölüm önerisi (default 0). Kullanıcının yeni
     *                            okuyucuda kaydedilmiş ilerlemesi varsa **yok sayılır**.
     * @param initialScrollOffset Opsiyonel: başlangıç scroll offset'i (dikey modda piksel,
     *                            sayfa modunda sayfa indeksi).
     * @param legacyPageNumber    Eski okuyucudan miras kalan tek-int sayfa numarası. **0 veya daha
     *                            küçük değerler yok sayılır.** Sadece kullanıcının yeni okuyucuda
     *                            kaydedilmiş ilerlemesi yoksa uygulanır; dikey modda kaba bir
     *                            piksel tahmini ile (bkz.
     *                            [com.kryptow.epub.reader.bookreader.ui.screen.reader.ESTIMATED_PIXELS_PER_LEGACY_PAGE]),
     *                            sayfa modunda doğrudan sayfa indeksi olarak.
     *
     * Örnek (Kotlin):
     * ```kotlin
     * // Default — eski API ile aynı davranış
     * EpubReader.openFile(context, "/storage/.../alice.epub")
     *
     * // Legacy reader migration
     * EpubReader.openFile(context, filePath, legacyPageNumber = legacy.pageNumber)
     * ```
     *
     * Örnek (Java):
     * ```java
     * EpubReader.openFile(context, filePath);                          // 2-arg
     * EpubReader.openFile(context, filePath, 0, 0, 124);               // full
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun openFile(
        context: Context,
        filePath: String,
        initialChapter: Int = 0,
        initialScrollOffset: Int = 0,
        legacyPageNumber: Int = -1,
    ) {
        openUri(
            context = context,
            uri = Uri.fromFile(File(filePath)),
            initialChapter = initialChapter,
            initialScrollOffset = initialScrollOffset,
            legacyPageNumber = legacyPageNumber,
        )
    }

    /**
     * Content/File URI'sinden kitabı açar.
     * Kitap daha önce eklenmemişse otomatik olarak veritabanına kaydedilir.
     *
     * Tüm opsiyonel parametreler [openFile] ile aynı semantiğe sahip.
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
    @JvmOverloads
    fun openUri(
        context: Context,
        uri: Uri,
        initialChapter: Int = 0,
        initialScrollOffset: Int = 0,
        legacyPageNumber: Int = -1,
    ) {
        context.startActivity(
            Intent(context, EpubReaderActivity::class.java).apply {
                putExtra(EpubReaderActivity.EXTRA_FILE_URI, uri)
                if (initialChapter > 0) putExtra(EpubReaderActivity.EXTRA_INITIAL_CHAPTER, initialChapter)
                if (initialScrollOffset > 0) putExtra(EpubReaderActivity.EXTRA_INITIAL_SCROLL_OFFSET, initialScrollOffset)
                if (legacyPageNumber > 0) putExtra(EpubReaderActivity.EXTRA_LEGACY_PAGE_NUMBER, legacyPageNumber)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    // ─── İç ────────────────────────────────────────────────────────────────────

    private fun internalModules(): List<Module> =
        listOf(appModule, databaseModule, repositoryModule, useCaseModule, viewModelModule)
}
