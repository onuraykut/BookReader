package com.kryptow.epub.reader.bookreader

import android.app.Application
import com.kryptow.epub.reader.EpubReader

class BookReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Tüm Koin modülleri (data, domain, ui) EpubReader kütüphanesi tarafından yönetilir.
        EpubReader.init(this)
    }
}
