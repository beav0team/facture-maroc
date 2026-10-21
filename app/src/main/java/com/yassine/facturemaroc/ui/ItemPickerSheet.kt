package com.yassine.facturemaroc.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore
import androidx.compose.material3.rememberModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerSheet(
    context: Context,
    onPickLine: (InvoiceLine) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var source by remember { mutableStateOf(PickerSource.ITEMS) }
    var query by remember { mutableStateOf("") }

    val savedItems = SavedItemStore(context)
    val items by savedItems.items.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            PickerTabs(
                source = source,
                onSelect = {
                    source = it
                    query = ""
                },
            )

            Spacer(Modifier.height(8.dp))

            when (source) {
                PickerSource.ITEMS -> ItemsSource(
                    store = savedItems,
                    query = query,
                    onQueryChange = { query = it },
                    onAdd = { item ->
                        onPickLine(item.toLine())
                    },
                )
                PickerSource.BY_HAND -> FreeEntry(
                    query = query,
                    heading = "Write the invoice by hand",
                    blurb = "Anything at all — a service, a part, labour, anything this app has never heard of. Nothing is looked up.",
                    onAdd = { brand, model, size, unit, price ->
                        val description = buildString {
                            if (model.isNotBlank()) append(model)
                            if (size.isNotBlank()) append(" $size")
                            if (unit.isNotBlank()) append(" ($unit)")
                        }
                        onPickLine(
                            InvoiceLine(
                                description = description.trim(),
                                quantity = 1,
                                unitPrice = price,
                                unit = unit,
                            ),
                        )
                    },
                    alsoSave = { savedItems.save(it) },
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PickerTabs(
    source: PickerSource,
    onSelect: (PickerSource) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerSource.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == source,
                onClick = { onSelect(candidate) },
                label = { Text(candidate.label) },
            )
        }
    }
}

@Composable
private fun ItemsSource(
    store: SavedItemStore,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: (SavedItem) -> Unit,
) {
    val items by store.items.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var addedCount by remember { mutableStateOf(0) }

    val shown = remember(items, query) { store.search(query) }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search your items") },
        placeholder = { Text("alignment  ·  fitting  ·  tube") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )

    Spacer(Modifier.height(8.dp))

    if (items.isEmpty()) {
        Text(
            "Save what you sell here — a fitting, a service, a part — and it is one tap away on every invoice from now on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    } else {
        Column(Modifier.fillMaxWidth()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(shown, key = { it.label }) { item ->
                    SavedItemRow(
                        item = item,
                        onClick = {
                            onAdd(item)
                            addedCount++
                        },
                    )
                }
                // Inline "New item" row at the bottom of the list
                item {
                    NewItemInlineRow(onClick = { creating = true })
                }
            }
        }
    }

    // Added counter at bottom
    if (addedCount > 0) {
        CounterBanner(count = addedCount)
    }

    if (creating) {
        SavedItemEditor(
            initial = SavedItem(label = query, price = 0.0),
            onSave = { newItem ->
                store.save(newItem)
                onAdd(newItem)
                addedCount++
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SavedItemRow(
    item: SavedItem,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.unit.isNotBlank()) {
                Text(
                    item.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            item.price.asDirhams(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NewItemInlineRow(
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "New item",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Save and add",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CounterBanner(count: Int) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            "$count added to this invoice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FreeEntry(
    query: String,
    heading: String,
    blurb: String,
    onAdd: (brand: String, model: String, size: String, unit: String, price: Double) -> Unit,
    alsoSave: ((SavedItem) -> Unit)? = null,
) {
    var description by remember(query) { mutableStateOf(query) }
    var brand by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var addedCount by remember { mutableStateOf(0) }

    val amount = price.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canAdd = amount > 0.0 && (description.isNotBlank() || size.isNotBlank())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Text(
            blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("On the invoice, call it") },
            placeholder = { Text("Wheel alignment  ·  Tourisme 135") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = size,
                onValueChange = { size = it },
                label = { Text("Size") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Per") },
                placeholder = { Text("per tyre  ·  per wheel") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Leave the brand and size empty for a service or labour.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = price,
            onValueChange = { input -> price = input.filter { it.isDigit() || it == '.' || it == ',' } },
            label = { Text("Price each (DH)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.Button(
                onClick = {
                    onAdd(brand.trim(), description.trim(), size.trim(), unit.trim(), amount)
                    alsoSave?.invoke(
                        SavedItem(
                            label = description.ifBlank { size.trim() },
                            price = amount,
                            unit = unit.trim(),
                        ),
                    )
                    addedCount++
                },
                enabled = canAdd,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (alsoSave != null) "Save and add" else "Add to invoice")
            }
        }
        if (addedCount > 0) {
            CounterBanner(count = addedCount)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SavedItemEditor(
    initial: SavedItem,
    onSave: (SavedItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var priceText by remember {
        mutableStateOf(if (initial.price > 0.0) initial.price.trimZeros() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "It goes on this invoice and stays on the list for next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("What it is") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        priceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Price each (DH)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = draft.unit,
                    onValueChange = { draft = draft.copy(unit = it) },
                    label = { Text("Per") },
                    placeholder = { Text("per tyre  ·  per wheel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        draft.copy(
                            price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
                enabled = draft.label.isNotBlank() &&
                    (priceText.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0,
            ) { Text("Save and add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

public enum class PickerSource(val label: String) {
    ITEMS("My items"),
    BY_HAND("By hand"),
}
