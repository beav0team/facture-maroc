package com.yassine.facturemaroc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore

/**
 * Adds lines to the invoice from either saved articles or free typing.
 *
 * The sheet stays open after adding, because a real invoice is several lines
 * and closing after each one turns a ten-second job into five reopenings. The
 * counter at the bottom is the only feedback needed that the tap landed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerSheet(
    store: SavedItemStore,
    onPickLine: (InvoiceLine) -> Unit,
    onDismiss: () -> Unit,
    /** The shop's price basis, so a new article is priced the way the shop quotes. */
    pricesIncludeVat: Boolean = false,
    vatPercent: Double = 0.0,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val folders by store.folders.collectAsState()
    var source by remember { mutableStateOf(PickerSource.ARTICLES) }
    var query by remember { mutableStateOf("") }
    var addedCount by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ajouter un article", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerSource.entries.forEach { candidate ->
                    ChoiceChip(
                        label = candidate.label,
                        selected = candidate == source,
                        onClick = {
                            source = candidate
                            query = ""
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when (source) {
                PickerSource.ARTICLES -> ArticlesSource(
                    store = store,
                    query = query,
                    onQueryChange = { query = it },
                    onAdd = { item ->
                        onPickLine(item.toLine())
                        addedCount++
                    },
                    onCreate = { creating = true },
                )

                PickerSource.FREE -> FreeEntry(
                    onAdd = { line ->
                        onPickLine(line)
                        addedCount++
                    },
                )
            }

            if (addedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (addedCount) {
                            1 -> "1 ligne ajoutée à cette facture"
                            else -> "$addedCount lignes ajoutées à cette facture"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Terminer")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (creating) {
        ItemEditorDialog(
            initial = null,
            onSave = { item ->
                // The label is the catalogue's key, so a second article under
                // the same name would replace the first — and with it the
                // quantity on the shelf. A name that already exists is not a
                // new article; it is that article, and it goes on the invoice.
                val existing = store.items.value.firstOrNull {
                    it.label.trim().equals(item.label.trim(), ignoreCase = true)
                }
                if (existing != null) {
                    onPickLine(existing.toLine())
                } else {
                    store.save(item)
                    onPickLine(item.toLine())
                }
                addedCount++
                creating = false
            },
            onDismiss = { creating = false },
            folders = folders,
            pricesIncludeVat = pricesIncludeVat,
            vatPercent = vatPercent,
        )
    }
}

@Composable
private fun ArticlesSource(
    store: SavedItemStore,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: (SavedItem) -> Unit,
    onCreate: () -> Unit,
) {
    val items by store.items.collectAsState()
    val shown = remember(items, query) { store.search(query) }
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val canScan = remember(context) { hasCamera(context) }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Rechercher dans mes articles") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        // The counter is where a box is opened, so the scan belongs here too:
        // typing thirteen digits in front of a customer is the slow path.
        trailingIcon = if (canScan) {
            {
                IconButton(onClick = { scanning = true }) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Scanner un code-barres",
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )

    if (scanning) {
        BarcodeScannerDialog(
            onScanned = { value ->
                // The code becomes the query, so the item appears in the list
                // and the shop taps it — the same gesture as a typed search.
                onQueryChange(value)
                scanning = false
            },
            onDismiss = { scanning = false },
        )
    }

    Spacer(Modifier.height(8.dp))

    if (items.isEmpty()) {
        Text(
            "Enregistrez ici ce que vous vendez souvent : une prestation, une pièce, " +
                "un service. Cela reste privé et ne se remplit qu'une fois.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(shown, key = { it.label.lowercase() }) { item ->
            SavedArticleRow(item = item, onClick = { onAdd(item) })
        }
        if (shown.isEmpty() && items.isNotEmpty()) {
            item(key = "no-match") {
                Text(
                    "Aucun article ne correspond à « $query ».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        item(key = "new-item") {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            NewArticleRow(onClick = onCreate)
        }
    }
}

@Composable
private fun SavedArticleRow(item: SavedItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The path, drawn with arrows: two folders can hold a "Hiver" and
            // the shop needs to see which one this article came from.
            val subtitle = listOf(SavedItemStore.display(item.folder), item.unit)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            item.price.asDirhams(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        // The shelf the line is about to come off, when there is one. Shown in
        // the picker because that is the last moment before the shop commits.
        if (item.tracksStock) {
            Spacer(Modifier.width(10.dp))
            Text(
                when (val stock = item.stock) {
                    null -> ""
                    0 -> "Épuisé"
                    else -> "$stock"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (item.outOfStock) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun NewArticleRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Nouvel article",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "L'enregistrer et l'ajouter à cette facture",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FreeEntry(onAdd: (InvoiceLine) -> Unit) {
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canAdd = description.isNotBlank() && price > 0.0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Désignation *") },
            placeholder = { Text("Prestation, main-d'œuvre, produit…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unité") },
                placeholder = { Text("heure, unité…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { input ->
                    priceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                },
                label = { Text("Prix (DH) *") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Button(
            onClick = {
                onAdd(
                    InvoiceLine(
                        description = description.trim(),
                        quantity = 1,
                        unitPrice = price,
                        unit = unit.trim(),
                    ),
                )
                description = ""
                unit = ""
                priceText = ""
            },
            enabled = canAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ajouter à la facture")
        }
        Spacer(Modifier.height(4.dp))
    }
}

private enum class PickerSource(val label: String) {
    ARTICLES("Mes articles"),
    FREE("Saisie libre"),
}
