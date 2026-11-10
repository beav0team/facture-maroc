package com.yassine.facturemaroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.Purchase
import com.yassine.facturemaroc.data.PurchaseLine
import com.yassine.facturemaroc.data.SavedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The goods receipts, newest first.
 *
 * A dialog rather than a fourth tab: a receipt is stock work, and the Articles
 * tab is where the shop is when it receives a delivery. The list keeps the
 * number, the supplier and the total, which is what a paper delivery note is
 * checked against.
 */
@Composable
fun PurchasesDialog(
    purchases: List<Purchase>,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onDelete: (Purchase) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Purchase?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Achats et réceptions") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Chaque réception ajoute les quantités au stock et retient le prix " +
                        "d'achat de l'article. Supprimer un bon retire ce qu'il avait apporté.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (purchases.isEmpty()) {
                    Text(
                        "Aucun bon de réception pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    purchases.forEachIndexed { index, purchase ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        PurchaseRow(
                            purchase = purchase,
                            onDelete = { pendingDelete = purchase },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNew, shape = MaterialTheme.shapes.small) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Nouveau bon")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )

    pendingDelete?.let { purchase ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer ce bon ?") },
            text = {
                Text(
                    "« ${purchase.number} » sera supprimé et les ${purchase.unitCount} unité" +
                        (if (purchase.unitCount > 1) "s" else "") +
                        " qu'il a apportées seront retirées du stock. " +
                        "Le mouvement reste dans l'historique.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(purchase)
                        pendingDelete = null
                    },
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun PurchaseRow(purchase: Purchase, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                purchase.number,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildList {
                    if (purchase.supplierName.isNotBlank()) add(purchase.supplierName)
                    if (purchase.date.isNotBlank()) add(purchase.date)
                    add(
                        if (purchase.lines.size == 1) {
                            "1 ligne"
                        } else {
                            "${purchase.lines.size} lignes"
                        },
                    )
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                purchase.totalHt.asDirhams(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "HT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Supprimer le bon ${purchase.number}",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The compact receipt editor: who delivered, what arrived, what it cost.
 *
 * The supplier is a picker over the saved suppliers plus a free-text field,
 * because a shop receives from a one-off seller it will never save. The article
 * is always picked from the catalogue: a receipt must move a real shelf, and a
 * typed label would create stock nothing can find later.
 */
@Composable
fun PurchaseEditorDialog(
    number: String,
    items: List<SavedItem>,
    suppliers: List<InvoiceClient>,
    onDismiss: () -> Unit,
    onSave: (Purchase) -> Unit,
) {
    // Only a counted article can be received; a service has no shelf.
    val received = remember(items) { items.filter { it.stock != null } }
    var supplierName by remember { mutableStateOf("") }
    var supplierIce by remember { mutableStateOf("") }
    var supplierMenu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<PurchaseLine>>(emptyList()) }

    var picked by remember { mutableStateOf<SavedItem?>(null) }
    var picker by remember { mutableStateOf(false) }
    var quantityText by remember { mutableStateOf("1") }
    var costText by remember { mutableStateOf("") }

    val quantity = quantityText.toIntOrNull() ?: 0
    val cost = costText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canAdd = picked != null && quantity > 0 && cost >= 0.0
    val total = lines.sumOf { it.total }

    fun addLine() {
        val item = picked ?: return
        if (!canAdd) return
        lines = lines + PurchaseLine(
            itemId = item.id,
            label = item.label,
            quantity = quantity,
            unitCostHt = cost,
        )
        // Clear the picker so the next line is a deliberate choice: a receipt
        // with two lines of the same article at different prices must say so,
        // not inherit the previous pick.
        picked = null
        quantityText = "1"
        costText = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Bon de réception")
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it },
                        label = { Text("Fournisseur") },
                        singleLine = true,
                        trailingIcon = {
                            if (suppliers.isNotEmpty()) {
                                IconButton(onClick = { supplierMenu = true }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Choisir un fournisseur",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = supplierMenu,
                        onDismissRequest = { supplierMenu = false },
                    ) {
                        suppliers.forEach { supplier ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (supplier.ice.isBlank()) {
                                            supplier.name
                                        } else {
                                            "${supplier.name} — ICE ${supplier.ice}"
                                        },
                                    )
                                },
                                onClick = {
                                    supplierName = supplier.name
                                    supplierIce = supplier.ice
                                    supplierMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = supplierIce,
                    onValueChange = { supplierIce = it.filter { c -> c.isDigit() }.take(15) },
                    label = { Text("ICE du fournisseur (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (received.isEmpty()) {
                    Text(
                        "Aucun article en stock à réceptionner. Créez d'abord un article " +
                            "dans l'onglet Articles.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Articles reçus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lines.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    "${line.quantity} × ${line.unitCostHt.asDirhams()} HT",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                line.total.asDirhams(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(
                                onClick = { lines = lines.filterNot { it === line } },
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Retirer ${line.label}",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { picker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(picked?.label ?: "Choisir un article")
                        }
                        DropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                            received.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    onClick = {
                                        picked = item
                                        picker = false
                                        // Start from what the article already
                                        // costs; the delivery usually confirms it.
                                        costText = if (item.purchasePrice > 0.0) {
                                            item.purchasePrice.trimZeros()
                                        } else {
                                            ""
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { input ->
                                quantityText = input.filter { it.isDigit() }.take(6)
                            },
                            label = { Text("Quantité") },
                            singleLine = true,
                            modifier = Modifier.width(104.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { input ->
                                costText = input.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            label = { Text("Prix d'achat HT") },
                            singleLine = true,
                            suffix = { Text("DH") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        IconButton(
                            onClick = { addLine() },
                            enabled = canAdd,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter la ligne")
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total HT", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            total.asDirhams(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Purchase(
                            id = UUID.randomUUID().toString(),
                            number = number,
                            date = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date()),
                            supplierName = supplierName.trim(),
                            supplierIce = supplierIce.trim(),
                            lines = lines,
                            note = note.trim(),
                        ),
                    )
                },
                enabled = lines.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Enregistrer la réception")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
