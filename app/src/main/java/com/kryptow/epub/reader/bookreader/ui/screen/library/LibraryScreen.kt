package com.kryptow.epub.reader.bookreader.ui.screen.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kryptow.epub.reader.R as LibR
import com.kryptow.epub.reader.bookreader.domain.model.Book
import com.kryptow.epub.reader.bookreader.domain.model.LibrarySortOrder
import com.kryptow.epub.reader.bookreader.domain.model.ReadingStatusFilter
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val allBooks by viewModel.allBooks.collectAsState()
    val recentBooks by viewModel.recentBooks.collectAsState()
    val favoriteBooks by viewModel.favoriteBooks.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val readingStats by viewModel.readingStats.collectAsState()
    val dailyGoal by viewModel.dailyGoalMinutes.collectAsState()

    var actionTarget by remember { mutableStateOf<Book?>(null) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importFile(it)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importFolder(it)
        }
    }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(info) {
        info?.let { snackbarHostState.showSnackbar(it); viewModel.clearInfo() }
    }

    val displayedBooks = when (selectedTab) {
        LibraryTab.ALL -> allBooks
        LibraryTab.RECENT -> recentBooks
        LibraryTab.FAVORITES -> favoriteBooks
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(LibR.string.library_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(LibR.string.library_sort),
                            )
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            LibrarySortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(order.labelRes())) },
                                    leadingIcon = {
                                        if (order == sortOrder) Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (viewMode == LibraryViewMode.GRID)
                                Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(LibR.string.library_change_view),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(LibR.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(LibR.string.library_scan_folder))
                }
                FloatingActionButton(
                    onClick = {
                        epubPicker.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/pdf",
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(LibR.string.library_add_book))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ─── Arama çubuğu ─────────────────────────────────────────────
            LibrarySearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onClear = { viewModel.clearSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ─── Okuma özeti (hedef · seri · son 7 gün) ────────────────────
            if (selectedTab == LibraryTab.ALL && (allBooks.isNotEmpty() || readingStats.totalSeconds > 0 ||
                    statusFilter != ReadingStatusFilter.ALL)
            ) {
                ReadingStatsCard(
                    stats = readingStats,
                    onClick = { showGoalDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ─── Sekme çubuğu ─────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == LibraryTab.ALL,
                    onClick = { viewModel.selectTab(LibraryTab.ALL) },
                    text = { Text("${stringResource(LibR.string.library_tab_all)} (${allBooks.size})") },
                )
                Tab(
                    selected = selectedTab == LibraryTab.RECENT,
                    onClick = { viewModel.selectTab(LibraryTab.RECENT) },
                    text = { Text(stringResource(LibR.string.library_tab_recent)) },
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    text = { Text(stringResource(LibR.string.library_tab_favorites)) },
                )
            }

            // ─── Okuma durumu filtresi (Son Okunan sekmesinde anlamsız) ────
            if (selectedTab != LibraryTab.RECENT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReadingStatusFilter.entries.forEach { f ->
                        FilterChip(
                            selected = statusFilter == f,
                            onClick = { viewModel.setStatusFilter(f) },
                            label = { Text(stringResource(f.labelRes())) },
                        )
                    }
                }
            }

            // ─── İçerik ───────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    displayedBooks.isEmpty() && statusFilter != ReadingStatusFilter.ALL &&
                        selectedTab != LibraryTab.RECENT ->
                        Text(
                            text = stringResource(LibR.string.library_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                        )

                    isLoading && displayedBooks.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    displayedBooks.isEmpty() ->
                        EmptyState(
                            tab = selectedTab,
                            onAddBook = {
                                epubPicker.launch(
                                    arrayOf(
                                        "application/epub+zip",
                                        "application/pdf",
                                        "application/octet-stream",
                                        "*/*",
                                    )
                                )
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )

                    viewMode == LibraryViewMode.GRID ->
                        BookGrid(
                            books = displayedBooks,
                            onBookClick = onBookClick,
                            onLongClick = { actionTarget = it },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                        )

                    else ->
                        BookList(
                            books = displayedBooks,
                            onBookClick = onBookClick,
                            onLongClick = { actionTarget = it },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                        )
                }

                // Arka plan yükleme çubuğu (ekstra kitap eklenirken)
                if (isLoading && displayedBooks.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    actionTarget?.let { book ->
        BookActionsSheet(
            book = book,
            onDismiss = { actionTarget = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(book)
                actionTarget = null
            },
            onToggleFinished = {
                viewModel.setFinished(book, !book.isFinished)
                actionTarget = null
            },
            onRemove = {
                actionTarget = null
                deleteTarget = book
            },
        )
    }

    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(LibR.string.library_delete_confirm)) },
            text = { Text(stringResource(LibR.string.book_remove_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book)
                    deleteTarget = null
                }) {
                    Text(stringResource(LibR.string.book_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(LibR.string.cancel)) }
            },
        )
    }

    if (showGoalDialog) {
        DailyGoalDialog(
            currentMinutes = dailyGoal,
            onSelect = { viewModel.setDailyGoal(it) },
            onDismiss = { showGoalDialog = false },
        )
    }
}

// ─── Kitap aksiyonları (uzun basma) ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionsSheet(
    book: Book,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFinished: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(
                        if (book.isFavorite) LibR.string.book_favorite_remove else LibR.string.book_favorite_add
                    ))
                },
                leadingContent = {
                    Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                },
                modifier = Modifier.clickable(onClick = onToggleFavorite),
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(
                        if (book.isFinished) LibR.string.book_mark_unfinished else LibR.string.book_mark_finished
                    ))
                },
                leadingContent = {
                    Icon(if (book.isFinished) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle, null)
                },
                modifier = Modifier.clickable(onClick = onToggleFinished),
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(LibR.string.book_remove), color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}

private fun LibrarySortOrder.labelRes(): Int = when (this) {
    LibrarySortOrder.LAST_READ -> LibR.string.sort_last_read
    LibrarySortOrder.DATE_ADDED -> LibR.string.sort_date_added
    LibrarySortOrder.TITLE -> LibR.string.sort_title
    LibrarySortOrder.AUTHOR -> LibR.string.sort_author
    LibrarySortOrder.PROGRESS -> LibR.string.sort_progress
}

private fun ReadingStatusFilter.labelRes(): Int = when (this) {
    ReadingStatusFilter.ALL -> LibR.string.library_tab_all
    ReadingStatusFilter.UNREAD -> LibR.string.filter_unread
    ReadingStatusFilter.READING -> LibR.string.filter_reading
    ReadingStatusFilter.FINISHED -> LibR.string.filter_finished
}

// ─── Boş durum ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    tab: LibraryTab,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (tab) {
        LibraryTab.ALL -> Icons.Default.Book
        LibraryTab.RECENT -> Icons.Default.History
        LibraryTab.FAVORITES -> Icons.Default.FavoriteBorder
    }
    val titleRes = when (tab) {
        LibraryTab.ALL -> LibR.string.library_empty_all_title
        LibraryTab.RECENT -> LibR.string.library_empty_recent_title
        LibraryTab.FAVORITES -> LibR.string.library_empty_favorites_title
    }
    val descRes = when (tab) {
        LibraryTab.ALL -> LibR.string.library_empty_all_desc
        LibraryTab.RECENT -> LibR.string.library_empty_recent_desc
        LibraryTab.FAVORITES -> LibR.string.library_empty_favorites_desc
    }

    Column(
        modifier = modifier.padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Large icon with tinted circle background
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (tab == LibraryTab.ALL) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onAddBook,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(LibR.string.library_add_book))
            }
        }
    }
}

// ─── Grid görünümü ────────────────────────────────────────────────────────────

@Composable
private fun BookGrid(
    books: List<Book>,
    onBookClick: (Long) -> Unit,
    onLongClick: (Book) -> Unit,
    onFavoriteClick: (Book) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookGridCard(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onLongClick(book) },
                onFavoriteClick = { onFavoriteClick(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Kapak
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.65f),
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = java.io.File(book.coverPath!!),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Favori butonu — kapak üstünde
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(LibR.string.library_favorite),
                        tint = if (book.isFavorite)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.isFinished) {
                    Spacer(Modifier.height(4.dp))
                    FinishedLabel()
                } else if (book.readingProgressPercent > 0f) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { book.readingProgressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "%.0f%%".format(book.readingProgressPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── Liste görünümü ───────────────────────────────────────────────────────────

@Composable
private fun BookList(
    books: List<Book>,
    onBookClick: (Long) -> Unit,
    onLongClick: (Book) -> Unit,
    onFavoriteClick: (Book) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookListRow(
                book = book,
                onClick = { onBookClick(book.id) },
                onLongClick = { onLongClick(book) },
                onFavoriteClick = { onFavoriteClick(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListRow(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Küçük kapak
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = java.io.File(book.coverPath!!),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Metin bilgileri
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.isFinished) {
                Spacer(Modifier.height(4.dp))
                FinishedLabel()
            } else if (book.readingProgressPercent > 0f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { book.readingProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${stringResource(LibR.string.reader_chapter_format, book.currentChapter + 1, book.totalChapters)}  •  %.0f%%".format(book.readingProgressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Favori butonu
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(LibR.string.library_favorite),
                tint = if (book.isFavorite)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Arama çubuğu ─────────────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(LibR.string.library_search_placeholder)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Ara")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Temizle")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun FinishedLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(LibR.string.filter_finished),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
