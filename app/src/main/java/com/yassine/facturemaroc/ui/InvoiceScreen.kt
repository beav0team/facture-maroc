package com.yassine.facturemaroc.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yassine.facturemaroc.data.AmountWords
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.ShopSettingsStore
import com.yassine.facturemaroc.invoice.InvoicePdf
import com.yassine.facturemaroc.data.InvoiceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    context: Context,
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    onOpenShopSettings: () -> Unit,
    onOpenClients: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val invoice by viewModel.invoice.collectAsState()
    val shop by viewModel.shopSettings.collectAsState()
    val design by viewModel.design.collectAsState()
    var showItemPicker by remember { mutableStateOf(false) }
    var showClientPicker by remember { mutableStateOf(false) }
    var showDesignPicker by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showDocTypeMenu by remember { mutableStateOf(false) }

    val lines = invoice.lines
    val client = invoice.client

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Invoice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenShopSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Shop settings")
                    }
                    IconButton(onClick = { showItemPicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add item")
                    }
                    IconButton(onClick = { showDesignPicker = true }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Design")
                    }
                    IconButton(onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            val outcome = viewModel.shareInvoice()
                            when (outcome) {
                                is InvoicePdf.Outcome.Shared -> snackbarHostState.showSnackbar("Invoice shared")
                                is InvoicePdf.Outcome.NoReceiver -> snackbarHostState.showSnackbar("PDF saved but no app to open it")
                                is InvoicePdf.Outcome.NotWritten -> snackbarHostState.showSnackbar("Failed to create PDF")
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Generate PDF")
                    }
                    IconButton(onClick = { showDocTypeMenu = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Document type")
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                ClientSelector(
                    client = client,
                    onClick = { showClientPicker = true },
                )

                Spacer(Modifier.height(8.dp))

                DocumentTypeSelector(
                    docType = invoice.documentType,
                    onClick = { showDocTypeMenu = true },
                )

                Spacer(Modifier.height(8.dp))

                if (lines.isEmpty()) {
                    EmptyLinesState(onAddItem = { showItemPicker = true })
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(lines) { index, line ->
                            InvoiceLineRow(
                                line = line,
                                index = index,
                                onEdit = { l: InvoiceLine -> viewModel.updateLine(index, l) },
                                onDelete = { viewModel.removeLine(index) },
                                onSetQuantity = { q: Int -> viewModel.setQuantity(index, q) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TotalsSection(
                    subtotal = invoice.subtotal,
                    discountPercent = invoice.discountPercent,
                    vatPercent = shop.vatPercent,
                    onDiscountClick = { showDiscountDialog = true },
                    onPaymentClick = { showPaymentDialog = true },
                )

                Spacer(Modifier.height(100.dp))
            }

            ExtendedFloatingActionButton(
                onClick = { showItemPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add item") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }

        if (showItemPicker) {
            ItemPickerSheet(
                context = context,
                onPickLine = { line ->
                    viewModel.addLine(line)
                    showItemPicker = false
                },
                onDismiss = { showItemPicker = false },
            )
        }

        if (showClientPicker) {
            ClientPickerSheet(
                onPickClient = { client ->
                    viewModel.setClient(client)
                    showClientPicker = false
                },
                onDismiss = { showClientPicker = false },
            )
        }

        if (showDesignPicker) {
            DesignPickerSheet(
                design = design,
                onPickDesign = { viewModel.setDesign(it) },
                onDismiss = { showDesignPicker = false },
            )
        }

        if (showDiscountDialog) {
            DiscountDialog(
                current = invoice.discountPercent,
                onSet = {
                    viewModel.setDiscountPercent(it)
                    showDiscountDialog = false
                },
                onDismiss = { showDiscountDialog = false },
            )
        }

        if (showPaymentDialog) {
            PaymentDialog(
                current = invoice.paymentMethod,
                onSet = {
                    viewModel.setPaymentMethod(it)
                    showPaymentDialog = false
                },
                onDismiss = { showPaymentDialog = false },
            )
        }

        if (showDocTypeMenu) {
            DocTypeDropdownMenu(
                expanded = showDocTypeMenu,
                onDismissRequest = { showDocTypeMenu = false },
                viewModel = viewModel,
            )
        }
    }
}

@Composable
fun DocTypeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: InvoiceViewModel,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = { Text("Facture") },
            onClick = { viewModel.setDocumentType(DocumentType.FACTURE); onDismissRequest() },
        )
        DropdownMenuItem(
            text = { Text("Devis") },
            onClick = { viewModel.setDocumentType(DocumentType.DEVIS); onDismissRequest() },
        )
        DropdownMenuItem(
            text = { Text("Bon de commande") },
            onClick = { viewModel.setDocumentType(DocumentType.BON_DE_COMMANDE); onDismissRequest() },
        )
        DropdownMenuItem(
            text = { Text("Ticket (80mm)") },
            onClick = { viewModel.setDocumentType(DocumentType.TICKET); onDismissRequest() },
        )
    }
}

@Composable
private fun ClientSelector(
    client: InvoiceClient?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    client?.name ?: "Select client",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (client != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (client != null) {
                    Text(
                        client.address ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentTypeSelector(
    docType: DocumentType,
    onClick: () -> Unit,
) {
    Text(
        text = docType.label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmptyLinesState(onAddItem: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddItem)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No items yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap to add your first item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvoiceLineRow(
    line: InvoiceLine,
    index: Int,
    onEdit: (InvoiceLine) -> Unit,
    onDelete: () -> Unit,
    onSetQuantity: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    line.description,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onSetQuantity(line.quantity - 1) }) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Decrease")
                    }
                    Text(
                        line.quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                    )
                    IconButton(onClick = { onSetQuantity(line.quantity + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (line.unitPrice * line.quantity * (1 - line.lineDiscountPercent / 100)).asDirhams(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = line.unitPrice.toString(),
                    onValueChange = { input ->
                        val price = input.replace(',', '.').toDoubleOrNull() ?: line.unitPrice
                        onEdit(line.copy(unitPrice = price))
                    },
                    label = { Text("Price") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = line.lineDiscountPercent.toString(),
                    onValueChange = { input ->
                        val disc = input.toIntOrNull() ?: line.lineDiscountPercent
                        onEdit(line.copy(lineDiscountPercent = disc.coerceIn(0, 100)))
                    },
                    label = { Text("Disc %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = line.unit,
                    onValueChange = { onEdit(line.copy(unit = it)) },
                    label = { Text("Per") },
                    placeholder = { Text("per tyre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { /* Edit functionality - placeholder */ }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun TotalsSection(
    subtotal: Double,
    discountPercent: Int,
    vatPercent: Double,
    onDiscountClick: () -> Unit,
    onPaymentClick: () -> Unit,
) {
    val discountAmount = subtotal * discountPercent / 100
    val afterDiscount = subtotal - discountAmount
    val vatAmount = afterDiscount * vatPercent / 100
    val total = afterDiscount + vatAmount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                Text(subtotal.asDirhams(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            if (discountPercent > 0) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Discount ($discountPercent%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-${discountAmount.asDirhams()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("After discount", style = MaterialTheme.typography.bodyMedium)
                Text(afterDiscount.asDirhams(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("VAT (${vatPercent.trimZeros()}%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vatAmount.asDirhams(), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(total.asDirhams(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Amount in words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    AmountWords.sentence(total),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDiscountClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Discount")
                }
                Button(
                    onClick = onPaymentClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Payment")
                }
            }
        }
    }
}

@Composable
private fun DiscountDialog(
    current: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discount %") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { it.isDigit() } },
                label = { Text("Discount percentage (0-100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(text.toIntOrNull()?.coerceIn(0, 100) ?: 0) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PaymentDialog(
    current: PaymentMethod,
    onSet: (PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment method") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { method ->
                    Text(
                        text = method.french,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable {
                                onSet(method)
                            }
                            .background(
                                if (method == current) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            ),
                        color = if (method == current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DesignPickerSheet(
    design: InvoiceDesign,
    onPickDesign: (InvoiceDesign) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { },
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Invoice design", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                InvoiceTemplate.entries.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable { onPickDesign(design.copy(template = t)); expanded = false },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(t.label, style = MaterialTheme.typography.bodyLarge)
                        if (t == design.template) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientPickerSheet(
    onPickClient: (InvoiceClient) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { },
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Select client", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text("Client picker - coming soon", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
