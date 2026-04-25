package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kryptow.epub.reader.bookreader.epub.model.EpubChapter
import com.kryptow.epub.reader.bookreader.ui.reader.ReadingColors

/**
 * İçindekiler / TOC bottom sheet.
 *
 * @param chapters Kitabın tüm bölümleri
 * @param currentIndex Aktif bölüm indeksi (vurgulanır, otomatik kaydırılır)
 * @param colors Okuma tema renkleri
 * @param sheetState ModalBottomSheet durumu
 * @param onChapterSelected Kullanıcı bir bölüme tıkladığında çağrılır
 * @param onDismiss Sheet kapatıldığında
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    chapters: List<EpubChapter>,
    currentIndex: Int,
    colors: ReadingColors,
    sheetState: SheetState,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Sheet açıldığında aktif bölümü ortaya kaydır
    LaunchedEffect(chapters, currentIndex) {
        if (chapters.isNotEmpty()) {
            val target = (currentIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (colors.isDark)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // Başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "İçindekiler",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${chapters.size} bölüm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            LazyColumn(state = listState) {
                itemsIndexed(chapters, key = { _, ch -> ch.href }) { index, chapter ->
                    TocRow(
                        index = index,
                        title = chapter.title.ifBlank { "Bölüm ${index + 1}" },
                        isActive = index == currentIndex,
                        onClick = { onChapterSelected(index) },
                    )
                }
                // Bottom sheet içindeki FAB'ın altına taşmaması için boşluk
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TocRow(
    index: Int,
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bölüm numarası baloncuğu
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
