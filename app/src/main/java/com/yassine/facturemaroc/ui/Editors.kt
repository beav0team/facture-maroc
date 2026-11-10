package com.yassine.facturemaroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.PaymentInfo
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.ItemField
import com.yassine.facturemaroc.data.PartyRole
import com.yassine.facturemaroc.data.PriceBasis
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Create or edit a client or a supplier.
 *
 * The legal identifiers are behind a disclosure because a walk-in customer is
 * a name and nothing else, and five tax fields on the first screen make the
 * common case look like paperwork.
 *
 * [defaultRole] is what a record created from here is, unless the shop says
 * otherwise with the chips: the picker that opened the dialog knows whether it
 * was opened for a client or a supplier, and the dialog must not silently
 * turn a supplier into a client.
 */
@Composable
fun ClientEditorDialog(
    initial: InvoiceClient?,
    onSave: (InvoiceClient) -> Unit,
    onDismiss: () -> Unit,
    defaultRole: PartyRole = PartyRole.CLIENT,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var role by remember(initial, defaultRole) { mutableStateOf(initial?.role ?: defaultRole) }
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
        title = {
            Text(
                when {
                    initial == null -> "Nouveau ${role.label.lowercase()}"
                    else -> "Modifier le ${role.label.lowercase()}"
                },
            )
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PartyRole.entries.forEach { candidate ->
                        ChoiceChip(
                            label = candidate.label,
                            selected = role == candidate,
                            onClick = { role = candidate },
                        )
                    }
                }
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
                            role = role,
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

/**
 * Create or edit one of the shop's own articles or services.
 *
 * A product and a service share this dialog because they share a shelf. The one
 * question that separates them is "does it get counted?", and it is asked with a
 * switch rather than by sending the shop to a second screen to decide which kind
 * of thing it is about to type.
 *
 * [pricesIncludeVat] and [vatPercent] are passed in so the sale price is asked
 * for in the basis the shop actually quotes — typing 150 into a field labelled
 * "prix de vente" and finding 180 on the invoice is the kind of surprise that
 * makes a shopkeeper stop trusting the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorDialog(
    initial: SavedItem?,
    onSave: (SavedItem) -> Unit,
    onDismiss: () -> Unit,
    folders: List<String> = emptyList(),
    defaultFolder: String = "",
    pricesIncludeVat: Boolean = false,
    vatPercent: Double = 0.0,
    /**
     * The optional fields the shop asked to see (see [visibleItemFields]).
     *
     * Defaults to all of them so a caller that predates the switches keeps the
     * old form rather than silently losing fields.
     */
    fields: Set<ItemField> = ItemField.entries.toSet(),
) {
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var barcode by remember(initial) { mutableStateOf(initial?.barcode ?: "") }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: "") }
    var folder by remember(initial) {
        mutableStateOf(initial?.folder ?: defaultFolder)
    }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var priceText by remember(initial) {
        mutableStateOf(initial?.price?.takeIf { it > 0.0 }?.trimZeros() ?: "")
    }
    var purchaseText by remember(initial) {
        mutableStateOf(initial?.purchasePrice?.takeIf { it > 0.0 }?.trimZeros() ?: "")
    }
    // A new article is assumed to be something on a shelf, because that is what
    // a shop's catalogue mostly is. Editing an old one keeps whatever it was.
    var tracksStock by remember(initial) { mutableStateOf(initial?.stock != null || initial == null) }
    var stockText by remember(initial) {
        mutableStateOf(initial?.stock?.toString() ?: "0")
    }
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Hidden on a device with no camera, where a button could only fail.
    val canScan = remember(context) { hasCamera(context) }

    val showBarcode = ItemField.BARCODE in fields
    val showPurchasePrice = ItemField.PURCHASE_PRICE in fields
    val showUnit = ItemField.UNIT in fields
    val showStock = ItemField.STOCK in fields
    val showFolder = ItemField.FOLDER in fields

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val purchase = purchaseText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val stock = stockText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val saleExVat = if (pricesIncludeVat) PriceBasis.excludingVat(price, vatPercent) else price
    val margin = if (purchase > 0.0 && price > 0.0) saleExVat - purchase else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Modifier l'article"
                    // With the stock field switched off the shop has no
                    // "prestation" notion at all, so the form is just an
                    // article — saying otherwise would explain a distinction
                    // the screen does not make.
                    !showStock -> "Nouvel article"
                    tracksStock -> "Nouvel article en stock"
                    else -> "Nouvelle prestation"
                },
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Désignation *") },
                    placeholder = { Text("Chaise en rotin, livraison, montage…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showBarcode) {
                    OutlinedTextField(
                        value = barcode,
                        // A scanned Code 128 label can carry letters, so the field
                        // takes whatever the scanner read rather than forcing digits.
                        onValueChange = { barcode = it },
                        label = { Text("Code-barres (facultatif)") },
                        placeholder = { Text("Les chiffres sous les barres") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                        trailingIcon = {
                            if (canScan) {
                                IconButton(onClick = { scanning = true }) {
                                    Icon(
                                        Icons.Filled.QrCodeScanner,
                                        contentDescription = "Scanner le code-barres",
                                    )
                                }
                            }
                        },
                    )
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        priceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = {
                        Text(
                            when {
                                !pricesIncludeVat -> "Prix de vente (DH HT) *"
                                vatPercent > 0.0 -> "Prix de vente (DH TTC) *"
                                else -> "Prix de vente (DH) *"
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                if (showPurchasePrice) {
                    OutlinedTextField(
                        value = purchaseText,
                        onValueChange = { input ->
                            purchaseText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Prix d'achat (DH HT, facultatif)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    if (margin != null) {
                        Text(
                            "Marge : ${margin.asDirhams()} par unité" +
                                if (purchase > 0.0) {
                                    " (${((margin / purchase) * 100.0).let { Math.round(it).toInt() }} %)"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (margin < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (showStock && tracksStock) {
                    /*
                     * The keyboard is for a number from a delivery note; the
                     * ± next to it is for the far more common "one more came
                     * in" and "one went out". It is the same gesture a shop
                     * already uses at the counter.
                     */
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { input -> stockText = input.filter { it.isDigit() }.take(6) },
                            label = { Text("En stock") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        IconButton(
                            onClick = {
                                stockText = (stock - 1).coerceAtLeast(0).toString()
                            },
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Retirer un")
                        }
                        IconButton(
                            onClick = {
                                if (stock < 999_999) stockText = (stock + 1).toString()
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter un")
                        }
                    }
                }
                if (showUnit) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unité") },
                        placeholder = { Text("unité, heure, kg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showStock) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("C'est une prestation", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Aucune quantité à décompter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = !tracksStock,
                            onCheckedChange = { service -> tracksStock = !service },
                        )
                    }
                }
                if (showFolder) {
                    ExposedDropdownMenuBox(
                        expanded = folderMenuOpen,
                        onExpandedChange = { folderMenuOpen = it },
                    ) {
                        OutlinedTextField(
                            value = if (folder.isBlank()) "Sans dossier" else SavedItemStore.display(folder),
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
                            folders.forEach { path ->
                                DropdownMenuItem(
                                    // The path, not just the name: "Hiver" can
                                    // exist under two different folders.
                                    text = { Text(SavedItemStore.display(path)) },
                                    onClick = {
                                        folder = path
                                        folderMenuOpen = false
                                    },
                                )
                            }
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
                            // A field the shop switched off keeps whatever the
                            // article already had: hiding it must never erase
                            // it, or turning the switch back on would show a
                            // blank where a real value used to be.
                            unit = if (showUnit) unit.trim() else initial?.unit.orEmpty(),
                            folder = if (showFolder) folder.trim() else initial?.folder.orEmpty(),
                            barcode = if (showBarcode) barcode.trim() else initial?.barcode.orEmpty(),
                            purchasePrice = if (showPurchasePrice) {
                                purchase
                            } else {
                                initial?.purchasePrice ?: 0.0
                            },
                            // The one that matters most: null here would turn a
                            // product into a service at the first edit.
                            stock = when {
                                !showStock -> initial?.stock
                                tracksStock -> stock
                                else -> null
                            },
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

    if (scanning) {
        BarcodeScannerDialog(
            onScanned = { value ->
                barcode = value
                scanning = false
            },
            onDismiss = { scanning = false },
        )
    }
}

/**
 * Names a folder, or renames one.
 *
 * Both actions ask for exactly one thing, so they share a dialog: the only
 * difference is the title and whether the field starts filled. [parent] is the
 * folder the new one is created inside, and is drawn rather than typed: a path
 * assembled by hand is a path with a typo in it, and a folder nobody can reach
 * is worse than no folder.
 */
@Composable
fun FolderEditorDialog(
    initial: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    parent: String = "",
) {
    var name by remember(initial) { mutableStateOf(initial ?: "") }
    // The separator is what makes a path a path, so a name carrying one would
    // silently create two levels instead of one folder called "A/B".
    val invalid = name.contains(SavedItemStore.SEPARATOR)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Renommer le dossier"
                    parent.isNotEmpty() -> "Nouveau sous-dossier"
                    else -> "Nouveau dossier"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (parent.isEmpty()) {
                        "Un dossier range les articles qui vont ensemble — boissons, prestations, " +
                            "pièces. Les dossiers peuvent contenir d'autres dossiers, et les " +
                            "articles restent utilisables sur toutes les factures."
                    } else {
                        "Il sera créé dans « ${SavedItemStore.display(parent)} ». Un sous-dossier " +
                            "sert à ranger ce qui va ensemble à l'intérieur d'un rayon — les coussins " +
                            "se rangent sous Textile."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du dossier *") },
                    placeholder = { Text("Boissons, Prestations, Pièces…") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text("Le caractère « / » sert à séparer les dossiers.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank() && !invalid,
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
 * One invoice line, including the fields that are quick to change on the phone:
 * quantity, price, and whether TVA applies to this line.
 */
@Composable
fun LineEditorDialog(
    initial: InvoiceLine,
    vatPercent: Double = 0.0,
    pricesIncludeVat: Boolean = false,
    onSave: (InvoiceLine) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // The shop quotes its prices one way; this dialog has to speak that way or
    // editing a line would silently move its price by the TVA rate every time
    // it is opened. An exempt line has no TVA inside it, so it is shown as-is.
    //
    // The answer comes from the switch, not from the line as it was when the
    // dialog opened: flipping "exemption" here changes whether the number in the
    // price field has TVA inside it. Reading only the initial flag divided the
    // typed price by the rate for an exempt line — 150 DH became 125 DH on the
    // document — and left a line that stopped being exempt charging HT + TVA
    // instead of the price that was typed.
    var taxExempt by remember(initial) { mutableStateOf(initial.taxExempt) }
    // Null means "the rate in the shop settings". A line only stores a rate of
    // its own when the shop changes it, so a shop that sells at one rate keeps
    // following Settings instead of freezing today's figure on every line.
    var rateChoice by remember(initial) { mutableStateOf(initial.vatPercent) }
    val effectiveRate = if (taxExempt) 0.0 else rateChoice ?: vatPercent
    val ttcBasis = pricesIncludeVat && !taxExempt && effectiveRate > 0.0
    var description by remember(initial) { mutableStateOf(initial.description) }
    var unit by remember(initial) { mutableStateOf(initial.unit) }
    var quantityText by remember(initial) { mutableStateOf(initial.quantity.toString()) }
    var priceText by remember(initial) {
        mutableStateOf(
            (
                // The number the shop typed, when it is still known: converting
                // the HT price back gives the same figure to the centime, but
                // only the stored one is exact.
                if (ttcBasis) initial.ttcPrice ?: PriceBasis.includingVat(initial.unitPrice, effectiveRate)
                else initial.unitPrice
                ).trimZeros(),
        )
    }
    var discountText by remember(initial) {
        mutableStateOf(if (initial.lineDiscountPercent > 0) initial.lineDiscountPercent.toString() else "")
    }
    // A discount given in dirhams is written the way the shop sees the price:
    // in the same basis as the price field. It is stored HT, so it is converted
    // on the way in and back on the way out, exactly like the unit price — a
    // shop that types TTC must get "−50 DH TTC" back, not "−41,67".
    var discountFixedText by remember(initial) {
        mutableStateOf(
            if (initial.lineDiscountFixed > 0.0) {
                (
                    if (ttcBasis) PriceBasis.includingVat(initial.lineDiscountFixed, effectiveRate)
                    else initial.lineDiscountFixed
                    ).trimZeros()
            } else {
                ""
            },
        )
    }

    val quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val discount = discountText.toIntOrNull()?.coerceIn(0, 100) ?: 0
    val discountFixed = discountFixedText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val gross = quantity * price
    val total = if (discountFixed > 0.0) {
        (gross - discountFixed).coerceAtLeast(0.0)
    } else {
        gross * (1.0 - discount / 100.0)
    }

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
                        label = { Text(if (ttcBasis) "Prix unitaire TTC (DH)" else "Prix unitaire (DH)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                // One discount per line: typing one clears the other, so the
                // line never carries two figures that would be added together.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { input ->
                            discountText = input.filter { it.isDigit() }.take(3)
                            if (discountText.isNotEmpty()) discountFixedText = ""
                        },
                        label = { Text("Remise (%)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    OutlinedTextField(
                        value = discountFixedText,
                        onValueChange = { input ->
                            discountFixedText =
                                input.filter { it.isDigit() || it == '.' || it == ',' }.take(10)
                            if (discountFixedText.isNotEmpty()) discountText = ""
                        },
                        label = { Text(if (ttcBasis) "Remise (DH TTC)" else "Remise (DH)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
                // Morocco taxes each line (CGI art. 145), so one invoice may
                // carry an article at 20 % and a service at 10 % on the same
                // page. "Document" keeps the line on the shop's own rate.
                Column(Modifier.fillMaxWidth()) {
                    Text("TVA de la ligne", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (rateChoice == null) {
                            "Taux du document : ${vatPercent.trimZeros()} %"
                        } else {
                            "Taux appliqué : ${effectiveRate.trimZeros()} %"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val choices = buildList<Pair<String, Double?>> {
                            add("Document" to null)
                            listOf(20.0, 10.0, 0.0).forEach { rate ->
                                add("${rate.trimZeros()} %" to rate)
                            }
                            // A rate a legacy document already carries stays
                            // offered, so reopening it cannot silently rewrite
                            // what the customer received.
                            initial.vatPercent
                                ?.takeIf { it != 20.0 && it != 10.0 && it != 0.0 }
                                ?.let { add("${it.trimZeros()} %" to it) }
                        }
                        choices.forEach { (label, value) ->
                            FilterChip(
                                selected = rateChoice == value,
                                onClick = { rateChoice = value },
                                label = { Text(label) },
                                enabled = !taxExempt,
                            )
                        }
                    }
                }
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
                            // Shown TTC means given TTC: converted once, here,
                            // because the invoice stores HT.
                            unitPrice = if (ttcBasis) {
                                PriceBasis.excludingVat(price, effectiveRate)
                            } else {
                                price
                            },
                            // Kept so a change of client can re-read the price
                            // instead of charging a different number than the
                            // one that was typed. [ttcBasis] is already false
                            // for an exempt line, which has no TVA inside it.
                            ttcPrice = if (ttcBasis) price else null,
                            lineDiscountPercent = discount,
                            lineDiscountFixed = if (discountFixed > 0.0) {
                                if (ttcBasis) {
                                    PriceBasis.excludingVat(discountFixed, effectiveRate)
                                } else {
                                    discountFixed
                                }
                            } else {
                                0.0
                            },
                            taxExempt = taxExempt,
                            vatPercent = rateChoice,
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

/**
 * The general discount, given either in percent or in dirhams.
 *
 * One discount per document: a shop says either "5 % de remise" or "200 DH de
 * remise", and choosing one clears the other. Only the fields for the chosen
 * mode are shown, so a figure can never be read as belonging to the other.
 */
@Composable
fun DiscountDialog(
    initialPercent: Int,
    initialAmount: Double,
    onConfirm: (percent: Int, amount: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var inAmount by remember(initialAmount) { mutableStateOf(initialAmount > 0.0) }
    var text by remember(initialPercent, initialAmount) {
        mutableStateOf(
            when {
                initialAmount > 0.0 -> initialAmount.trimZeros()
                initialPercent > 0 -> initialPercent.toString()
                else -> ""
            },
        )
    }
    val percent = text.toIntOrNull()?.coerceIn(0, 100) ?: 0
    val amount = text.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remise générale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = "Pourcentage",
                        selected = !inAmount,
                        onClick = { if (inAmount) { inAmount = false; text = "" } },
                    )
                    ChoiceChip(
                        label = "Montant",
                        selected = inAmount,
                        onClick = { if (!inAmount) { inAmount = true; text = "" } },
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = if (inAmount) {
                            input.filter { it.isDigit() || it == '.' || it == ',' }.take(10)
                        } else {
                            input.filter { it.isDigit() }.take(3)
                        }
                    },
                    label = { Text(if (inAmount) "Montant de la remise" else "Pourcentage") },
                    suffix = { Text(if (inAmount) "DH" else "%") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (inAmount) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (!inAmount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 10, 20).forEach { candidate ->
                            ChoiceChip(
                                label = if (candidate == 0) "Aucune" else "$candidate %",
                                selected = percent == candidate,
                                onClick = { text = if (candidate == 0) "" else candidate.toString() },
                            )
                        }
                    }
                }
                Text(
                    if (inAmount) {
                        "Retirée du total HT, avant la TVA. Jamais plus que le total."
                    } else {
                        "Appliquée au total HT, avant la TVA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inAmount) onConfirm(0, amount) else onConfirm(percent, 0.0)
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            Row {
                if (initialPercent > 0 || initialAmount > 0.0) {
                    TextButton(onClick = { onConfirm(0, 0.0) }) { Text("Retirer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
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

/**
 * The deposit already received on another document.
 *
 * It is entered as an amount and a reference, never as a percentage: an acompte
 * is a sum the customer actually paid, and a document that recomputed it would
 * disagree with the receipt they hold.
 */
@Composable
fun DepositDialog(
    initialAmount: Double,
    initialReference: String,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember(initialAmount) {
        mutableStateOf(if (initialAmount > 0.0) initialAmount.trimZeros() else "")
    }
    var reference by remember(initialReference) { mutableStateOf(initialReference) }
    val value = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acompte déjà versé") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        amount = input.filter { it.isDigit() || it == ',' || it == '.' }.take(12)
                    },
                    label = { Text("Montant TTC") },
                    suffix = { Text("DH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it.take(40) },
                    label = { Text("Document de l'acompte (facultatif)") },
                    placeholder = { Text("Facture d'acompte n°") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Déduit de ce que le client doit encore payer. La TVA n'est pas " +
                        "recalculée : l'acompte a déjà été taxé sur son propre document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value, reference.trim()) },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            Row {
                if (initialAmount > 0.0 || initialReference.isNotBlank()) {
                    TextButton(onClick = { onConfirm(0.0, "") }) { Text("Retirer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/**
 * Sets the day the money is due.
 *
 * Moroccan shops quote "comptant", "30 jours" or "60 jours", so those get a
 * chip each; a typed date covers the rest. The field is validated here rather
 * than on the invoice, because a deadline that cannot be parsed is a deadline
 * nobody will honour.
 */
@Composable
fun DueDateDialog(
    initial: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }
    fun daysFromNow(days: Int): String =
        formatter.format(Date(System.currentTimeMillis() + days * 86_400_000L))

    var text by remember(initial) { mutableStateOf(initial.orEmpty()) }
    val parsed = text.trim().let { value ->
        if (value.isEmpty()) null else runCatching { formatter.parse(value) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Échéance de paiement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Comptant" to 0,
                        "30 jours" to 30,
                        "60 jours" to 60,
                    ).forEach { (label, days) ->
                        ChoiceChip(
                            label = label,
                            selected = text.trim() == daysFromNow(days),
                            onClick = { text = daysFromNow(days) },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == '/' }.take(10)
                    },
                    label = { Text("Date (jj/mm/aaaa)") },
                    placeholder = { Text(daysFromNow(30)) },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    supportingText = {
                        Text(
                            if (text.isNotBlank() && parsed == null) {
                                "Date incomplète ou impossible."
                            } else {
                                "Imprimée sur le document et signalée en retard une fois passée."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim().ifEmpty { null }) },
                enabled = text.isBlank() || parsed != null,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            Row {
                if (!initial.isNullOrBlank()) {
                    TextButton(onClick = { onConfirm(null) }) { Text("Retirer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
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

/**
 * A free-text note for the document.
 *
 * Multi-line on purpose: "conditions de règlement" is a sentence, not a label,
 * and a single-line field hid everything after the first phrase. [initial] is
 * the value already on the invoice, so reopening the dialog shows what will
 * print rather than an empty box.
 */
@Composable
fun NoteDialog(
    title: String,
    initial: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = { onConfirm("") }) { Text("Effacer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/**
 * The extra charge printed between the discount and the TVA.
 *
 * The label is editable because "Livraison" is only the common case: a shop
 * also charges for fitting, a deposit or a delivery outside town. An empty
 * amount removes the row from the totals entirely rather than printing a zero.
 */
@Composable
fun SurchargeDialog(
    initialLabel: String,
    initialAmount: Double,
    initialPercent: Double,
    onConfirm: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    var inPercent by remember(initialPercent) { mutableStateOf(initialPercent > 0.0) }
    var text by remember(initialAmount, initialPercent) {
        mutableStateOf(
            when {
                initialPercent > 0.0 -> initialPercent.trimZeros()
                initialAmount > 0.0 -> initialAmount.trimZeros()
                else -> ""
            },
        )
    }
    val value = text.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Livraison / surcharge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Libellé") },
                    placeholder = { Text("Livraison") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = "Montant",
                        selected = !inPercent,
                        onClick = { if (inPercent) { inPercent = false; text = "" } },
                    )
                    ChoiceChip(
                        label = "Pourcentage",
                        selected = inPercent,
                        onClick = { if (!inPercent) { inPercent = true; text = "" } },
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = if (inPercent) {
                            input.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                        } else {
                            input.filter { it.isDigit() || it == ',' || it == '.' }.take(10)
                        }
                    },
                    label = { Text(if (inPercent) "Pourcentage du total HT" else "Montant HT") },
                    suffix = { Text(if (inPercent) "%" else "DH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
                Text(
                    if (inPercent) {
                        "Calculée sur le total après remise, puis soumise à la TVA."
                    } else {
                        "Ajouté après la remise, avant la TVA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = label.trim().ifBlank { "Livraison" }
                    if (inPercent) onConfirm(name, 0.0, value) else onConfirm(name, value, 0.0)
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            Row {
                if (initialAmount > 0.0 || initialPercent > 0.0) {
                    TextButton(onClick = { onConfirm("Livraison", 0.0, 0.0) }) {
                        Text("Retirer")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}


/**
 * Records what the shop received.
 *
 * The amount starts at the whole total because that is the common case — a
 * shop that is paid in full should not have to type the figure it just read on
 * the document. Typing less records a partial payment, and leaving it empty (or
 * removing an existing payment) puts the document back to unpaid.
 */
@Composable
fun PaymentDialog(
    record: InvoiceRecord,
    onConfirm: (PaymentInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    val total = record.data.total
    val current = record.data.payment
    val today = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
    }
    var amount by remember {
        mutableStateOf(
            if ((current?.paidAmount ?: 0.0) > 0.0) current!!.paidAmount.trimZeros() else total.trimZeros(),
        )
    }
    var date by remember { mutableStateOf(current?.paidAt?.takeIf { it.isNotBlank() } ?: today) }
    var note by remember { mutableStateOf(current?.note.orEmpty()) }
    // What actually came in. Defaults to what the document says, which is what
    // an untouched dialog records.
    var method by remember { mutableStateOf(current?.method ?: record.data.paymentMethod) }
    val parsed = amount.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paiement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Total TTC : ${total.asDirhams()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        amount = input.filter { it.isDigit() || it == ',' || it == '.' }.take(12)
                    },
                    label = { Text("Montant reçu") },
                    suffix = { Text("DH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                if (parsed > total) {
                    Text(
                        "Le montant sera plafonné au total (${total.asDirhams()}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Date (jj/mm/aaaa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.fillMaxWidth()) {
                    Text("Moyen de paiement", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethod.entries.forEach { candidate ->
                            ChoiceChip(
                                label = candidate.french,
                                selected = method == candidate,
                                onClick = { method = candidate },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text("Référence (facultatif)") },
                    placeholder = { Text("Chèque n°, virement…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "S'il reste moins que le total, le document indique le reste à payer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = amount.trim().replace(',', '.')
                    val value = cleaned.toDoubleOrNull()?.coerceIn(0.0, total) ?: 0.0
                    onConfirm(
                        if (value <= 0.0) {
                            null
                        } else {
                            PaymentInfo(
                                paidAmount = value,
                                paidAt = date.trim(),
                                note = note.trim(),
                                method = method,
                            )
                        },
                    )
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            Row {
                if (current != null && current.paidAmount > 0.0) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
