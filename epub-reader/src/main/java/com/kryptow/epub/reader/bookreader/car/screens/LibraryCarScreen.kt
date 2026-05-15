package com.kryptow.epub.reader.bookreader.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kryptow.epub.reader.R
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LibraryCarScreen(carContext: CarContext) : Screen(carContext), KoinComponent {

    private val bookRepository: BookRepository by inject()
    private var books: List<Book> = emptyList()
    private var loading = true

    private val screenJob = SupervisorJob()
    private val screenScope = CoroutineScope(Dispatchers.Main + screenJob)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
        screenScope.launch {
            bookRepository.getAllBooks().collect { bookList ->
                books = bookList
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!loading) {
            if (books.isEmpty()) {
                listBuilder.setNoItemsMessage(carContext.getString(R.string.car_library_empty))
            } else {
                books.forEach { book ->
                    val progress = (book.readingProgressPercent * 100).toInt()
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(book.title)
                            .addText(book.author)
                            .addText(
                                when {
                                    progress > 0 -> carContext.getString(R.string.car_progress_format, progress)
                                    else -> carContext.getString(R.string.car_chapters_count, book.totalChapters)
                                }
                            )
                            .setOnClickListener {
                                screenManager.push(BookDetailCarScreen(carContext, book))
                            }
                            .build()
                    )
                }
            }
        }

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_library))
            .apply {
                if (loading) setLoading(true)
                else setSingleList(listBuilder.build())
            }
            .build()
    }
}
