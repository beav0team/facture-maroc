package com.yassine.facturemaroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.StockEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One article's stock story, newest first.
 *
 * The screen exists because the quantity alone cannot answer the question a shop
 * actually asks — "why does it say three?" — and because a correction the shop
 * made in the editor and a sale the app recorded have to be told apart. Each row
 * names the reason, the document or count behind it, and the moment.
 *
 * Read-only on purpose: history is not editable. A mistake is corrected by
 * another movement, exactly like a document is corrected by a credit note.
 */
@Composable
fun StockHistoryDialog(
    item: SavedItem,
    entries: List<StockEntry>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historique de stock") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Text(
                    "Solde : ${item.stock ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text(
                        "Aucun mouvement enregistré.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        StockEntryRow(entry)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun StockEntryRow(entry: StockEntry) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.reason.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildList {
                    if (entry.reference.isNotBlank()) add(entry.reference)
                    add(stockDate(entry.at))
                    if (entry.note.isNotBlank()) add(entry.note)
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            // A sign is always printed: "+1" and "1" look the same in a list
            // where the direction is the whole point.
            if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (entry.delta > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/** `10/11/2026 14:32` — the same shape as the rest of the app's dates. */
private fun stockDate(at: Long): String =
    if (at <= 0L) "date inconnue"
    else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(at))
