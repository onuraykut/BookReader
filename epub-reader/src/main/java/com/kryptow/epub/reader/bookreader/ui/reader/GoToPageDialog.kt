package com.kryptow.epub.reader.bookreader.ui.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kryptow.epub.reader.R

/**
 * Düz sayfa numarasına gitme dialogu (HORIZONTAL_PAGE modu için).
 * Pagination indeksleme tamamlandıktan sonra aktif olur.
 */
@Composable
fun GoToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onConfirm: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val page = input.toIntOrNull()
    val isValid = page != null && page in 1..totalPages

    fun confirm() {
        if (isValid) onConfirm(page!! - 1) // 0-tabanlı
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_go_to_page)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() }.take(6) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("${stringResource(R.string.dialog_page_label)} (1 – $totalPages)") },
                placeholder = { Text("$currentPage") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { confirm() }),
                singleLine = true,
                isError = input.isNotBlank() && !isValid,
                supportingText = if (input.isNotBlank() && !isValid) {
                    { Text("1 — $totalPages") }
                } else null,
            )
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = isValid) { Text(stringResource(R.string.dialog_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
