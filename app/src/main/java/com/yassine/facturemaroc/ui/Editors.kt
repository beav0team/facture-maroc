package com.yassine.facturemaroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.SavedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Create or edit a client.
 *
 * The legal identifiers are behind a disclosure because a walk-in customer is
 * a name and nothing else, and five tax fields on the first screen make the
 * common case look like paperwork.
 */
@Composable
fun ClientEditorDialog(
    initial: InvoiceClient?,
    onSave: (InvoiceClient) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var email by remember(initial) { mutableStateOf(initial?.email ?: "") }
    var address by remember(initial) { mutableStateOf(initial?.address ?: "") }
    var ice by remember(initial) { mutableStateOf(initial?.ice ?: "") }
    var ifNumber by remember(initial) { mutableStateOf(initial?.ifNumber ?: "") }
    var rc by remember(initial) { mutableStateOf(initial?.rc ?: "") }
    var patente by remember(initial) { mutableStateOf(initial?.patente ?: "") }
    var cnss by remember(initial) { mutableStateOf(initial?.cnss ?: "") }
    var vatExempt by remember(initial) { mutableStateOf(initial?.vatExempt ?: false) }
    var legalOpen by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau client" else "Modifier le client") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom ou raison sociale *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                TextButton(
                    onClick = { legalOpen = !legalOpen },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(
                        if (legalOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (legalOpen) {
                            "Masquer les informations légales"
                        } else {
                            "Informations légales (ICE, IF, RC…)"
                        },
                    )
                }
                if (legalOpen) {
                    OutlinedTextField(
                        value = ice,
                        onValueChange = { ice = it },
                        label = { Text("ICE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ifNumber,
                        onValueChange = { ifNumber = it },
                        label = { Text("Identifiant fiscal (IF)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rc,
                        onValueChange = { rc = it },
                        label = { Text("Registre de commerce (RC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = patente,
                        onValueChange = { patente = it },
                        label = { Text("Patente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cnss,
                        onValueChange = { cnss = it },
                        label = { Text("CNSS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Exonéré de TVA", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "La TVA ne sera pas appliquée à ses factures.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vatExempt, onCheckedChange = { vatExempt = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        InvoiceClient(
                            name = name.trim(),
                            address = address.trim(),
                            ice = ice.trim(),
                            ifNumber = ifNumber.trim(),
                            rc = rc.trim(),
                            patente = patente.trim(),
                            cnss = cnss.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            vatExempt = vatExempt,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
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

/** Create or edit one of the shop's own articles or services. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorDialog(
    initial: SavedItem?,
    onSave: (SavedItem) -> Unit,
    onDismiss: () -> Unit,
    folders: List<String> = emptyList(),
    defaultFolder: String = "",
) {
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: "") }
    var folder by remember(initial) {
        mutableStateOf(initial?.folder ?: defaultFolder)
    }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var priceText by remember(initial) {
        mutableStateOf(initial?.price?.takeIf { it > 0.0 }?.trimZeros() ?: "")
    }
    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvel article" else "Modifier l'article") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Ce que vous vendez souvent, avec son prix. Il sera proposé en un geste " +
                        "sur chaque facture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Désignation *") },
                    placeholder = { Text("Prestation, main-d'œuvre, produit…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        priceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text("Prix unitaire (DH) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unité (facultatif)") },
                    placeholder = { Text("heure, jour, unité, kg…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = folderMenuOpen,
                    onExpandedChange = { folderMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = folder.ifBlank { "Sans dossier" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Dossier") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderMenuOpen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = folderMenuOpen,
                        onDismissRequest = { folderMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sans dossier") },
                            onClick = {
                                folder = ""
                                folderMenuOpen = false
                            },
                        )
                        folders.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    folder = name
                                    folderMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SavedItem(
                            label = label.trim(),
                            price = price,
                            unit = unit.trim(),
                            folder = folder.trim(),
                        ),
                    )
                },
                enabled = label.isNotBlank() && price > 0.0,
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

/**
 * Names a folder, or renames one.
 *
 * Both actions ask for exactly one thing, so they share a dialog: the only
 * difference is the title and whether the field starts filled.
 */
@Composable
fun FolderEditorDialog(
    initial: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau dossier" else "Renommer le dossier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Un dossier range les articles qui vont ensemble — boissons, prestations, " +
                        "pièces. Les articles restent utilisables sur toutes les factures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du dossier *") },
                    placeholder = { Text("Boissons, Prestations, Pièces…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
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

/**
 * The list of folders, with the two actions that can change one.
 *
 * Renaming and deleting are here rather than hidden behind a long-press: a
 * folder chip is a filter, and a filter that quietly becomes an editor when
 * held is a trap. Counts are shown because the question "what is in this one?"
 * comes before "what do I do with it?".
 */
@Composable
fun ManageFoldersDialog(
    folders: List<String>,
    counts: Map<String, Int>,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dossiers d'articles") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        "Aucun dossier. Créez-en un pour séparer, par exemple, les prestations " +
                            "des produits que vous vendez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                folders.forEach { folder ->
                    val count = counts[folder] ?: 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (count) {
                                    0 -> "Aucun article"
                                    1 -> "1 article"
                                    else -> "$count articles"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRename(folder) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Renommer $folder",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(folder) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Supprimer $folder",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nouveau dossier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

/**
 * One invoice line, including the fields that are quick to change on the phone:
 * quantity, price, and whether TVA applies to this line.
 */
@Composable
fun LineEditorDialog(
    initial: InvoiceLine,
    onSave: (InvoiceLine) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var description by remember(initial) { mutableStateOf(initial.description) }
    var unit by remember(initial) { mutableStateOf(initial.unit) }
    var quantityText by remember(initial) { mutableStateOf(initial.quantity.toString()) }
    var priceText by remember(initial) { mutableStateOf(initial.unitPrice.trimZeros()) }
    var discountText by remember(initial) {
        mutableStateOf(if (initial.lineDiscountPercent > 0) initial.lineDiscountPercent.toString() else "")
    }
    var taxExempt by remember(initial) { mutableStateOf(initial.taxExempt) }

    val quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val discount = discountText.toIntOrNull()?.coerceIn(0, 100) ?: 0
    val total = quantity * price * (1.0 - discount / 100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la ligne") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Désignation *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unité (facultatif)") },
                    placeholder = { Text("heure, unité, kg…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input ->
                            quantityText = input.filter { it.isDigit() }.take(6)
                        },
                        label = { Text("Quantité") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { input ->
                            priceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Prix unitaire (DH)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { input ->
                        discountText = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Remise sur la ligne (%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ligne exonérée de TVA", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "La TVA ne s'applique pas à cette ligne.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = taxExempt, onCheckedChange = { taxExempt = it })
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Total de la ligne",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        total.asDirhams(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Supprimer la ligne", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            description = description.trim(),
                            unit = unit.trim(),
                            quantity = quantity,
                            unitPrice = price,
                            lineDiscountPercent = discount,
                            taxExempt = taxExempt,
                        ),
                    )
                },
                enabled = description.isNotBlank(),
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

/** A percentage with a few one-tap values, because 95 % of them are 0–20. */
@Composable
fun PercentDialog(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(if (initial > 0) initial.toString() else "") }
    val value = text.toIntOrNull()?.coerceIn(0, 100) ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Pourcentage") },
                    suffix = { Text("%") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 20).forEach { candidate ->
                        ChoiceChip(
                            label = if (candidate == 0) "Aucune" else "$candidate %",
                            selected = value == candidate,
                            onClick = {
                                text = if (candidate == 0) "" else candidate.toString()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/** Picks the date until which a quote stays valid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidityDateDialog(
    initial: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }
    val initialMillis = remember(initial) {
        initial?.let { runCatching { formatter.parse(it)?.time }.getOrNull() }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(formatter.format(Date(millis)))
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    "Validité du devis",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            showModeToggle = false,
        )
    }
}
