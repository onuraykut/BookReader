package com.kryptow.epub.reader.bookreader

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.kryptow.epub.reader.EpubReader

class BookReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isDebuggable()) enableStrictMode()
        // Tüm Koin modülleri (data, domain, ui) + PDFBox EpubReader kütüphanesi tarafından yönetilir.
        EpubReader.init(this)
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Yalnızca debug derlemelerinde: ana thread'de disk/ağ erişimi, kapatılmamış
     * kaynaklar ve sızan Activity'ler logcat'e "StrictMode" etiketiyle yazılır.
     * ANR'ların çoğu bu ihlallerden doğar; release'i etkilemez (penaltyLog — çökertmez).
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
