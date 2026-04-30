package com.kryptow.epub.reader.bookreader.car

import androidx.car.app.Screen
import androidx.car.app.Session
import android.content.Intent
import com.kryptow.epub.reader.bookreader.car.screens.LibraryCarScreen

class BookReaderSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        LibraryCarScreen(carContext)
}
