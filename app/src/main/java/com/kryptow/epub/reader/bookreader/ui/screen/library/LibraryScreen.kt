package com.kryptow.epub.reader.bookreader.ui.screen.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kryptow.epub.reader.bookreader.domain.model.Book
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
            viewModel.importEpub(it)
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
                title = { Text("Kütüphane") },
                actions = {
                    // Grid / List toggle
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (viewMode == LibraryViewMode.GRID)
                                Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Görünüm değiştir",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
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
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Klasör Tara")
                }
                FloatingActionButton(
                    onClick = {
                        epubPicker.launch(
                            arrayOf("application/epub+zip", "application/octet-stream", "*/*")
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "EPUB Ekle")
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

            // ─── Sekme çubuğu ─────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == LibraryTab.ALL,
                    onClick = { viewModel.selectTab(LibraryTab.ALL) },
                    text = { Text("Tümü (${allBooks.size})") },
                )
                Tab(
                    selected = selectedTab == LibraryTab.RECENT,
                    onClick = { viewModel.selectTab(LibraryTab.RECENT) },
                    text = { Text("Son Okunanlar") },
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    text = { Text("Favoriler") },
                )
            }

            // ─── İçerik ───────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && displayedBooks.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    displayedBooks.isEmpty() ->
                        EmptyState(tab = selectedTab, modifier = Modifier.align(Alignment.Center))

                    viewMode == LibraryViewMode.GRID ->
                        BookGrid(
                            books = displayedBooks,
                            onBookClick = onBookClick,
                            onLongClick = { viewModel.deleteBook(it) },
                            onFavoriteClick = { viewModel.toggleFavorite(it) },
                        )

                    else ->
                        BookList(
                            books = displayedBooks,
                            onBookClick = onBookClick,
                            onLongClick = { viewModel.deleteBook(it) },
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
}

// ─── Boş durum ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(tab: LibraryTab, modifier: Modifier = Modifier) {
    val (icon, title, subtitle) = when (tab) {
        LibraryTab.ALL -> Triple(
            Icons.Default.Book,
            "Kütüphaneniz boş",
            "+ ile EPUB ekleyin veya klasör tarayın",
        )
        LibraryTab.RECENT -> Triple(
            Icons.Default.Book,
            "Henüz okuma yapmadınız",
            "Bir kitap açarak başlayın",
        )
        LibraryTab.FAVORITES -> Triple(
            Icons.Default.FavoriteBorder,
            "Favori kitap yok",
            "Kitap kartındaki ♡ ile favorilere ekleyin",
        )
    }
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        model = book.coverPath,
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
                        contentDescription = "Favori",
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
                if (book.readingProgressPercent > 0f) {
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
                    model = book.coverPath,
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
            if (book.readingProgressPercent > 0f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { book.readingProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Bölüm ${book.currentChapter + 1} / ${book.totalChapters}  •  %.0f%%".format(book.readingProgressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Favori butonu
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favori",
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
        placeholder = { Text("Başlık veya yazar ara...") },
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
