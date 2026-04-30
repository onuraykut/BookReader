package com.kryptow.epub.reader.bookreader.automotive

import android.app.Application
import com.kryptow.epub.reader.EpubReader

class AutomotiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EpubReader.init(this)
    }
}
