package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.bookreader.ui.screen.reader.SearchResult

/**
 * Kitap içi arama bottom sheet.
 * - Üstte arama çubuğu (autofocus)
 * - Aktif sayfada kaç eşleşme var + önceki/sonraki butonları
 * - Altta tüm bölümlerdeki sonuçların listesi; tıklayınca o bölüme atlar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    query: String,
    results: List<SearchResult>,
    currentChapterMatchCount: Int,
    currentChapterMatchIndex: Int,
    sheetState: SheetState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            // ─── Başlık ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kapat")
                }
                Spacer(Modifier.width(4.dp))
                Text("Kitapta Ara", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))

            // ─── Arama kutusu ─────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Aranacak kelimeyi girin…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Temizle")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )

            Spacer(Modifier.height(8.dp))

            // ─── Mevcut bölüm eşleşme navigasyonu ────────────────────────
            if (currentChapterMatchCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Bu sayfada: $currentChapterMatchIndex / $currentChapterMatchCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        IconButton(onClick = onPrevMatch, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Önceki")
                        }
                        IconButton(onClick = onNextMatch, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, "Sonraki")
                        }
                    }
                }
            } else if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    text = "Sonuç bulunamadı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Tüm bölümlerdeki sonuçlar ────────────────────────────────
            if (results.isNotEmpty()) {
                Text(
                    text = "${results.size} bölümde bulundu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results) { result ->
                        SearchResultRow(
                            result = result,
                            query = query,
                            onClick = { onResultClick(result) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = "Bölüm ${result.chapterIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        // Eşleşen kısmı kalın göster
        val snippet = result.textSnippet
        val queryLower = query.lowercase()
        val snippetLower = snippet.lowercase()
        val matchStart = snippetLower.indexOf(queryLower)
        val annotated = buildAnnotatedString {
            if (matchStart >= 0) {
                append(snippet.substring(0, matchStart))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                    append(snippet.substring(matchStart, minOf(matchStart + query.length, snippet.length)))
                }
                if (matchStart + query.length < snippet.length) {
                    append(snippet.substring(matchStart + query.length))
                }
            } else {
                append(snippet)
            }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
    }
}
