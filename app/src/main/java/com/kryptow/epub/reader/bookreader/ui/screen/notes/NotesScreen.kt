package com.kryptow.epub.reader.bookreader.ui.screen.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.bookreader.domain.model.Highlight
import com.kryptow.epub.reader.bookreader.domain.model.HighlightColor
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: NotesViewModel = koinViewModel(parameters = { parametersOf(bookId) }),
) {
    val highlights by viewModel.highlights.collectAsState()
    val colorFilter by viewModel.colorFilter.collectAsState()
    val editTarget by viewModel.editTarget.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notlar ve Vurgular") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                    }
                },
                actions = {
                    // TXT dışa aktarma / paylaşma
                    IconButton(onClick = {
                        val txt = viewModel.exportAsTxt()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, txt)
                            putExtra(Intent.EXTRA_SUBJECT, "Notlar ve Vurgular")
                        }
                        context.startActivity(Intent.createChooser(intent, "Dışa Aktar"))
                    }) {
                        Icon(Icons.Default.Share, "Dışa Aktar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ─── Renk filtresi ────────────────────────────────────────────
            ColorFilterRow(
                selected = colorFilter,
                onSelect = { viewModel.setColorFilter(it) },
            )

            // ─── Liste ────────────────────────────────────────────────────
            if (highlights.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (colorFilter == null) "Henüz vurgu yok"
                            else "${colorFilter!!.labelTr} renkli vurgu yok",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(highlights, key = { it.id }) { highlight ->
                        HighlightCard(
                            highlight = highlight,
                            onEdit = { viewModel.startEdit(highlight) },
                            onDelete = { viewModel.delete(highlight) },
                        )
                    }
                }
            }
        }
    }

    // ─── Not düzenleme dialogu ────────────────────────────────────────────────
    editTarget?.let { target ->
        NoteEditDialog(
            initialNote = target.note ?: "",
            onConfirm = { viewModel.saveNote(it) },
            onDismiss = { viewModel.cancelEdit() },
        )
    }
}

// ─── Renk filtre çubuğu ───────────────────────────────────────────────────────

@Composable
private fun ColorFilterRow(
    selected: HighlightColor?,
    onSelect: (HighlightColor?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Tümü") },
        )
        HighlightColor.entries.forEach { color ->
            FilterChip(
                selected = selected == color,
                onClick = { onSelect(if (selected == color) null else color) },
                label = { Text(color.labelTr) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color(android.graphics.Color.parseColor(color.hex))),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(android.graphics.Color.parseColor(color.hex))
                        .copy(alpha = 0.3f),
                ),
            )
        }
    }
}

// ─── Vurgu kartı ──────────────────────────────────────────────────────────────

@Composable
private fun HighlightCard(
    highlight: Highlight,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accentColor = Color(android.graphics.Color.parseColor(highlight.color.hex))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Renkli şerit + seçili metin
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(accentColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\"${highlight.selectedText}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    highlight.note?.let { note ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Alt çubuk: bölüm bilgisi + tarih + eylemler
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bölüm ${highlight.page + 1}  •  ${formatDate(highlight.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Not ekle/düzenle",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Sil",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ─── Not düzenleme dialogu ────────────────────────────────────────────────────

@Composable
private fun NoteEditDialog(
    initialNote: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not Ekle / Düzenle") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notunuz") },
                placeholder = { Text("Bu vurgu için not yazın...") },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale("tr")).format(Date(ts))
