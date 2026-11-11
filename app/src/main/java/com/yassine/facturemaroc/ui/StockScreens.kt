package com.yassine.facturemaroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.StockEntry
import com.yassine.facturemaroc.data.Stocktake
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

/**
 * Every article's movements in one list.
 *
 * The per-article history answers "why is this three?"; this answers "what
 * moved today?", which is the question a shop asks after closing, when it is
 * checking that the day's invoices and receipts all landed.
 */
@Composable
fun StockJournalDialog(
    items: List<SavedItem>,
    /** Newest first, as the caller read them. */
    entries: List<StockEntry>,
    onDismiss: () -> Unit,
) {
    val labels = remember(items) { items.associate { it.id to it.label } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mouvements du stock") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                        StockEntryRow(
                            entry = entry,
                            title = labels[entry.itemId] ?: "Article supprimé",
                        )
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
private fun StockEntryRow(
    entry: StockEntry,
    /** What the movement was about: the reason for one article, its name for the shop-wide journal. */
    title: String = entry.reason.label,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                buildList {
                    if (title != entry.reason.label) add(entry.reason.label)
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

/**
 * The physical count, one line per counted article.
 *
 * The sheet asks for a number the shop can see on the shelf, not a correction:
 * the app works out the difference. It starts every field at the expected
 * quantity because a count mostly confirms what is already known, and typing
 * forty numbers to change one would make the feature a punishment.
 *
 * A reason is required as soon as there is a difference, because the ledger is
 * read months later by someone who will not remember why the shelf moved. An
 * unchanged count needs no story and writes nothing.
 */
@Composable
fun StocktakeDialog(
    items: List<SavedItem>,
    /** What the ledger says for an article, which may differ from its cache. */
    expectedOf: (SavedItem) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (counts: Map<String, Int>, reason: String) -> Unit,
) {
    val expected = remember(items) {
        items.filter { it.stock != null }.associate { it.id to expectedOf(it) }
    }
    val texts = remember(items) {
        mutableStateMapOf<String, String>().apply {
            expected.forEach { (id, value) -> put(id, value.toString()) }
        }
    }
    var reason by remember { mutableStateOf("") }

    fun counted(id: String): Int =
        texts[id]?.toIntOrNull() ?: expected[id] ?: 0

    val rows = remember(texts.toMap(), expected) {
        Stocktake.rows(
            items = items,
            counts = expected.mapValues { (id, _) -> counted(id) },
            expectedOf = { item -> expected[item.id] ?: 0 },
        )
    }
    val summary = remember(rows) { Stocktake.summary(rows) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inventaire") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (items.none { it.stock != null }) {
                    Text(
                        "Aucun article en stock à compter.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    return@Column
                }
                Text(
                    "Saisissez ce qui est réellement sur l'étagère. Seuls les écarts " +
                        "seront enregistrés dans l'historique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                items.filter { it.stock != null }.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                            val difference = counted(item.id) - (expected[item.id] ?: 0)
                            Text(
                                buildString {
                                    append("Attendu : ${expected[item.id] ?: 0}")
                                    if (difference != 0) {
                                        append(" · écart ")
                                        append(if (difference > 0) "+$difference" else difference.toString())
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (difference == 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        OutlinedTextField(
                            value = texts[item.id].orEmpty(),
                            onValueChange = { input ->
                                texts[item.id] = input.filter { it.isDigit() }.take(6)
                            },
                            singleLine = true,
                            label = { Text("Compté") },
                            modifier = Modifier.width(104.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Attendu ${summary.expectedUnits} · Compté ${summary.countedUnits} · " +
                                "Écart ${if (summary.differenceUnits > 0) "+" else ""}${summary.differenceUnits}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (summary.differenceValue == 0.0) {
                                "Aucun écart de valeur."
                            } else {
                                "Valeur de l'écart : ${summary.differenceValue.asDirhams()}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif de l'écart *") },
                    placeholder = { Text("Casse, vol, erreur de saisie…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val counts = items.filter { it.stock != null }.associate { it.id to counted(it.id) }
                    onConfirm(counts, reason.trim())
                },
                // No difference, no entry: recording a count that changed
                // nothing would put a line in every article's history for a
                // perfect inventory.
                enabled = summary.adjusted > 0 && reason.isNotBlank(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
