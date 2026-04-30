package com.kryptow.epub.reader.bookreader.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.kryptow.epub.reader.bookreader.car.screens.LibraryCarScreen

class BookReaderCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = BookReaderSession()
}
