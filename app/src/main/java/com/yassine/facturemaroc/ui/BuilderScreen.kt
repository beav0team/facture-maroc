package com.yassine.facturemaroc.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.PartyRole
import com.yassine.facturemaroc.data.XmlExportOutcome
import com.yassine.facturemaroc.invoice.InvoicePdf
import com.yassine.facturemaroc.invoice.InvoiceValidation
import com.yassine.facturemaroc.invoice.InvoiceXml
import com.yassine.facturemaroc.invoice.ValidationCode
import com.yassine.facturemaroc.invoice.ValidationIssue
import kotlinx.coroutines.launch

/**
 * The invoice being written.
 *
 * One vertical list, in the order the document is read: what kind of document,
 * who it is for, what is on it, how it is paid, and what it all comes to. Every
 * action that adds something lives next to the thing it adds to — the old
 * screen had the same "add item" in three places, none of them where the lines
 * were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val invoice by viewModel.invoice.collectAsState()
    val shop by viewModel.shop.collectAsState()
    val design by viewModel.design.collectAsState()
    val clients by viewModel.clients.clients.collectAsState()

    val lines = invoice.lines
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    /** Live PDF preview of the current draft, rendered by the same code that writes the file. */
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(invoice, design) {
        if (invoice.lines.isEmpty()) {
            previewBitmap = null
        } else {
            previewBitmap = viewModel.previewBitmap(720)
        }
    }

    var showItemPicker by remember { mutableStateOf(false) }
    var showClientPicker by remember { mutableStateOf(false) }
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showDesignPicker by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showValidityDialog by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val validationIssues = remember(invoice) { InvoiceValidation.validate(invoice) }
    val blockingIssues = remember(validationIssues) {
        validationIssues.filter { it.code != ValidationCode.NO_LINES }
    }

    val xmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (viewModel.exportDraftXml(uri)) {
                    XmlExportOutcome.Saved -> onBack()
                    XmlExportOutcome.Failed ->
                        snackbar.showSnackbar("Le fichier XML n'a pas pu être enregistré.")
                    XmlExportOutcome.NothingToExport ->
                        snackbar.showSnackbar("Ajoutez au moins un article avant d'exporter.")
                }
            }
        }
    }

    fun leave() {
        viewModel.saveDraftNow()
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Column {
                        Text(
                            invoice.header,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            invoice.number,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDesignPicker = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Modèle de document")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Modèle et logo") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showDesignPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Paramètres du magasin") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.saveDraftNow()
                                    onOpenSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remise générale") },
                                leadingIcon = { Icon(Icons.Filled.Percent, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showDiscountDialog = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Exporter en XML") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Description, contentDescription = null)
                                },
                                enabled = lines.isNotEmpty() && blockingIssues.isEmpty(),
                                onClick = {
                                    menuOpen = false
                                    xmlLauncher.launch(InvoiceXml.fileName(invoice))
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            TotalsBar(
                total = invoice.total,
                vatAmount = invoice.vatAmount,
                enabled = lines.isNotEmpty() && blockingIssues.isEmpty() && !sharing,
                busy = sharing,
                onShare = {
                    scope.launch {
                        sharing = true
                        val outcome = viewModel.shareInvoice()
                        sharing = false
                        if (outcome is InvoicePdf.Outcome.Shared) {
                            onBack()
                        } else {
                            snackbar.showSnackbar(outcome.shareMessage())
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "document-type") {
                DocumentTypeRow(
                    selected = invoice.documentType,
                    onSelect = viewModel::setDocumentType,
                )
            }

            if (invoice.showReferenceField) {
                item(key = "reference") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Référence du document",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = invoice.referenceNumber,
                            onValueChange = { viewModel.setReferenceNumber(it) },
                            label = { Text("Annule et remplace la facture N°") },
                            placeholder = { Text("Ex: F-20261001-0042") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                }
            }

            if (invoice.showIncoterm) {
                item(key = "incoterm") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Incoterms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = invoice.incoterm,
                            onValueChange = { viewModel.setIncoterm(it) },
                            label = { Text("Incoterms (ex: FOB, EXW, CIF)") },
                            placeholder = { Text("Facultatif") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                }
            }

            // One party block, always the one the document prints: the client
            // on a sale, the supplier on a bon de commande. A "Client" field on
            // an order form only invited the shop to type itself in.
            if (invoice.documentType != DocumentType.BON_DE_COMMANDE) {
                item(key = "client") {
                    ClientBlock(
                        client = invoice.client,
                        onPick = { showClientPicker = true },
                        onClear = viewModel::clearClient,
                    )
                }
            }

            if (invoice.documentType == DocumentType.BON_DE_COMMANDE) {
                item(key = "supplier") {
                    SupplierBlock(
                        supplier = invoice.supplier,
                        onPick = { showSupplierPicker = true },
                        onClear = { viewModel.setSupplier(null) },
                    )
                }
            }

            item(key = "lines-heading") {
                SectionHeading(
                    "Articles",
                    trailing = when (lines.size) {
                        0 -> "Aucun"
                        1 -> "1 ligne"
                        else -> "${lines.size} lignes"
                    },
                )
            }

            if (lines.isEmpty()) {
                item(key = "empty-lines") {
                    AddFirstItemCard(onClick = { showItemPicker = true })
                }
            } else {
                itemsIndexed(
                    lines,
                    key = { index, line -> "line-$index-${line.description}" },
                ) { index, line ->
                    LineCard(
                        line = line,
                        onEdit = { editingLine = index },
                        onQuantity = { quantity -> viewModel.setQuantity(index, quantity) },
                        onDelete = { viewModel.remove(index) },
                    )
                }
            }

            if (lines.isNotEmpty()) {
                item(key = "add-line") {
                    OutlinedButton(
                        onClick = { showItemPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter un article")
                    }
                }
            }

            item(key = "options") {
                OptionsCard(
                    discountPercent = invoice.discountPercent,
                    paymentMethod = invoice.paymentMethod,
                    vatPercent = invoice.effectiveVatPercent,
                    pricesIncludeVat = shop.pricesIncludeVat,
                    showPayment = invoice.showPaymentMethod,
                    isDevis = invoice.isDevis,
                    validityDate = invoice.validityDate,
                    onDiscount = { showDiscountDialog = true },
                    onPayment = viewModel::setPaymentMethod,
                    onValidity = { showValidityDialog = true },
                    onOpenSettings = {
                        viewModel.saveDraftNow()
                        onOpenSettings()
                    },
                )
            }

            if (blockingIssues.isNotEmpty()) {
                item(key = "validation") {
                    ValidationCard(issues = blockingIssues)
                }
            }

            if (lines.isNotEmpty()) {
                item(key = "words") {
                    AmountWordsCard(invoice.amountInWords)
                }
            }

            if (previewBitmap != null) {
                val bitmap = previewBitmap
                item(key = "preview") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(vertical = 8.dp),
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Aperçu du document",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showItemPicker) {
        ItemPickerSheet(
            store = viewModel.savedItems,
            onPickLine = viewModel::addLine,
            onDismiss = { showItemPicker = false },
            pricesIncludeVat = shop.pricesIncludeVat,
            vatPercent = invoice.effectiveVatPercent,
        )
    }

    if (showClientPicker) {
        ClientPickerSheet(
            title = "Choisir un client",
            clients = clients,
            role = PartyRole.CLIENT,
            onPick = {
                viewModel.setClient(it)
                showClientPicker = false
            },
            onCreate = {
                viewModel.clients.save(it)
                viewModel.setClient(it)
                showClientPicker = false
            },
            onClear = {
                viewModel.clearClient()
                showClientPicker = false
            },
            clearLabel = "Client de passage",
            onDismiss = { showClientPicker = false },
        )
    }

    if (showSupplierPicker) {
        ClientPickerSheet(
            title = "Choisir un fournisseur",
            clients = clients,
            role = PartyRole.SUPPLIER,
            onPick = {
                viewModel.setSupplier(it)
                showSupplierPicker = false
            },
            onCreate = {
                viewModel.clients.save(it)
                viewModel.setSupplier(it)
                showSupplierPicker = false
            },
            onClear = {
                viewModel.setSupplier(null)
                showSupplierPicker = false
            },
            clearLabel = "Aucun fournisseur",
            onDismiss = { showSupplierPicker = false },
        )
    }

    if (showDesignPicker) {
        DesignPickerSheet(
            viewModel = viewModel,
            design = design,
            onSelect = viewModel::setDesign,
            onDismiss = { showDesignPicker = false },
        )
    }

    if (showDiscountDialog) {
        PercentDialog(
            title = "Remise générale",
            initial = invoice.discountPercent,
            onConfirm = {
                viewModel.setDiscountPercent(it)
                showDiscountDialog = false
            },
            onDismiss = { showDiscountDialog = false },
        )
    }

    if (showValidityDialog) {
        ValidityDateDialog(
            initial = invoice.validityDate,
            onConfirm = {
                viewModel.setValidityDate(it)
                showValidityDialog = false
            },
            onDismiss = { showValidityDialog = false },
        )
    }

    editingLine?.let { index ->
        val line = lines.getOrNull(index)
        if (line == null) {
            editingLine = null
        } else {
            LineEditorDialog(
                initial = line,
                vatPercent = invoice.effectiveVatPercent,
                pricesIncludeVat = shop.pricesIncludeVat,
                onSave = {
                    viewModel.updateLine(index, it)
                    editingLine = null
                },
                onDelete = {
                    viewModel.remove(index)
                    editingLine = null
                },
                onDismiss = { editingLine = null },
            )
        }
    }
}

@Composable
private fun DocumentTypeRow(
    selected: DocumentType,
    onSelect: (DocumentType) -> Unit,
) {
    // Two per row: "Bon de commande" is too long to sit beside three other
    // chips on a phone without pushing the last one off the edge, and a chip
    // cut in half reads as a broken control rather than as a scroll hint.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    ChoiceChip(
                        label = type.label,
                        selected = type == selected,
                        onClick = { onSelect(type) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (type == selected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientBlock(
    client: InvoiceClient,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val initials = remember(client.name) {
        client.name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }

    Card(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (client.name.isBlank()) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ajouter un client", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Facultatif — une facture peut rester au nom du porteur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        client.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        client.phone.ifBlank { null },
                        client.ice.ifBlank { null }?.let { "ICE $it" },
                    ).joinToString("  ·  ")
                    Text(
                        subtitle.ifBlank { "Appuyez pour modifier" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Retirer le client",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupplierBlock(
    supplier: InvoiceClient?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        onClick = onPick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Fournisseur", style = MaterialTheme.typography.labelMedium)
                Text(
                    supplier?.name?.ifBlank { null } ?: "Aucun fournisseur",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (supplier != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Retirer le fournisseur",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddFirstItemCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("Ajouter le premier article", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "Depuis vos articles enregistrés, ou en saisie libre.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LineCard(
    line: InvoiceLine,
    onEdit: () -> Unit,
    onQuantity: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    line.description,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.unit.isNotBlank()) {
                    Text(
                        "Unité : ${line.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(quantity = line.quantity, onChange = onQuantity)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${line.unitPrice.asDirhams()} l'unité",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (line.lineDiscountPercent > 0 || line.taxExempt) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (line.lineDiscountPercent > 0) {
                            StatusPill(
                                text = "−${line.lineDiscountPercent} %",
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (line.taxExempt) {
                            StatusPill(
                                text = "TVA exonérée",
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    line.total.asDirhams(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Supprimer la ligne",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                icon = Icons.Filled.Remove,
                description = "Diminuer la quantité",
                onClick = { onChange(quantity - 1) },
            )
            Text(
                quantity.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(26.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton(
                icon = Icons.Filled.Add,
                description = "Augmenter la quantité",
                onClick = { onChange(quantity + 1) },
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OptionsCard(
    discountPercent: Int,
    paymentMethod: PaymentMethod,
    vatPercent: Double,
    pricesIncludeVat: Boolean,
    showPayment: Boolean,
    isDevis: Boolean,
    validityDate: String?,
    onDiscount: () -> Unit,
    onPayment: (PaymentMethod) -> Unit,
    onValidity: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OptionRow(
                icon = Icons.Filled.Percent,
                label = "Remise générale",
                value = if (discountPercent == 0) "Aucune" else "−$discountPercent %",
                onClick = onDiscount,
            )

            if (isDevis) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OptionRow(
                    icon = Icons.Filled.CalendarMonth,
                    label = "Valable jusqu'au",
                    value = validityDate ?: "Non précisée",
                    onClick = onValidity,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "TVA",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (vatPercent == 0.0) "Exonérée" else "${vatPercent.trimZeros()} %",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pricesIncludeVat && vatPercent > 0.0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "prix TTC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Modifier")
                }
            }

            if (showPayment) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                    Text(
                        "Mode de paiement",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PaymentMethod.entries, key = { it.id }) { method ->
                            ChoiceChip(
                                label = method.french,
                                selected = method == paymentMethod,
                                onClick = { onPayment(method) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ValidationCard(issues: List<ValidationIssue>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "À corriger avant l'envoi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            issues.forEach { issue ->
                Text(
                    "• ${issue.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun AmountWordsCard(sentence: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Montant en lettres",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sentence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TotalsBar(
    total: Double,
    vatAmount: Double,
    enabled: Boolean,
    busy: Boolean,
    onShare: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Total TTC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    total.asDirhams(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (vatAmount > 0.0) {
                    Text(
                        "dont TVA ${vatAmount.asDirhams()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onShare,
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Création…" else "Partager")
            }
        }
    }
}
