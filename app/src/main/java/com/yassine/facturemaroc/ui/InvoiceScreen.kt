package com.yassine.facturemaroc.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoicePdf
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.PaymentMethod
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    val lines by viewModel.lines.collectAsState()
    val client by viewModel.client.collectAsState()
    val discount by viewModel.discountPercent.collectAsState()
    val payment by viewModel.paymentMethod.collectAsState()
    val shop by viewModel.shop.collectAsState()
    val clients by viewModel.clients.clients.collectAsState()
    val suppliers by viewModel.suppliers.clients.collectAsState()
    val documentType by viewModel.documentType.collectAsState()
    val validityDate by viewModel.validityDate.collectAsState()
    val supplier by viewModel.supplier.collectAsState()

    var clientMenuOpen by remember { mutableStateOf(false) }
    var supplierMenuOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }

    val preview = remember(lines, client, discount, payment, shop, documentType, validityDate, supplier) {
        InvoiceData(
            number = viewModel.nextNumber(),
            date = InvoicePdf.today(),
            client = client,
            lines = lines,
            discountPercent = discount,
            vatPercent = shop.vatPercent,
            paymentMethod = payment,
            documentType = documentType,
            validityDate = validityDate,
            supplier = supplier,
        )
    }

    val shopIncomplete = shop.name.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(preview.header) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = lines.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear the invoice")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shopIncomplete) {
                NoticeCard(
                    "Your shop details are not filled in yet.",
                    "An invoice without the shop's name and ICE is not a document a customer can rely on.",
                ) {
                    onOpenShopSettings()
                }
            }

            DocumentTypeSelector(
                currentType = documentType,
                onSelect = { viewModel.setDocumentType(it) },
            )

            Spacer(Modifier.height(4.dp))

            if (documentType == DocumentType.DEVIS) {
                OutlinedTextField(
                    value = validityDate.orEmpty(),
                    onValueChange = { viewModel.setValidityDate(it.ifBlank { null }) },
                    label = { Text("Validité jusqu'au") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }

            if (documentType == DocumentType.BON_DE_COMMANDE) {
                SupplierSelector(
                    supplier = supplier,
                    suppliers = suppliers,
                    onSelect = { viewModel.setSupplier(it) },
                    onOpenManage = { supplierMenuOpen = true },
                )
                if (supplierMenuOpen) {
                    SupplierDropdown(
                        suppliers = suppliers,
                        onSelect = { viewModel.setSupplier(it); supplierMenuOpen = false },
                        onOpenManage = { supplierMenuOpen = false; onOpenClients() },
                        onDismiss = { supplierMenuOpen = false },
                    )
                }
            } else {
                ClientSelector(
                    client = client,
                    clients = clients,
                    onChange = { viewModel.setClient(it) },
                    onOpenManage = { clientMenuOpen = true },
                )
                if (clientMenuOpen) {
                    ClientDropdown(
                        clients = clients,
                        onSelect = { viewModel.setClient(it); clientMenuOpen = false },
                        onOpenManage = { clientMenuOpen = false; onOpenClients() },
                        onDismiss = { clientMenuOpen = false },
                    )
                }
            }

            if (lines.isEmpty()) {
                EmptyInvoice()
            } else {
                HorizontalDivider()
                lines.forEachIndexed { index, line ->
                    InvoiceLineRow(
                        line = line,
                        onSetQuantity = { viewModel.setQuantity(index, it) },
                        onPriceChange = { viewModel.setUnitPrice(index, it) },
                        onDiscountChange = { viewModel.setLineDiscount(index, it) },
                        onRemove = { viewModel.remove(index) },
                    )
                }
                HorizontalDivider()
            }

            OutlinedButton(
                onClick = { picking = true },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (lines.isEmpty()) "Add items" else "Add more items"
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = if (discount == 0) "" else discount.toString(),
                onValueChange = { viewModel.setDiscount(it.filter(Char::isDigit).take(2).toIntOrNull() ?: 0) },
                label = { Text("Discount %") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Number),
            )

            Text(
                "TVA ${trimPercentForUi(shop.vatPercent)}%  ·  set in shop details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total TTC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    invoiceMoney(preview.total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (discount > 0) {
                TotalLine("Discount −${trimPercentForUi(discount.toDouble())}%", "−" + invoiceMoney(preview.discountAmount))
            }
            TotalLine("TVA ${trimPercentForUi(preview.effectiveVatPercent)}%", invoiceMoney(preview.vatAmount))

            Text(
                preview.amountInWords,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (documentType == DocumentType.FACTURE) {
                Text(
                    "Payment",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = payment == method,
                            onClick = { viewModel.setPaymentMethod(method) },
                            label = { Text(method.french) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { previewOpen = true },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Preview")
                }
                Button(
                    onClick = {
                        scope.launch {
                            sharing = true
                            val outcome = viewModel.shareInvoice()
                            sharing = false
                            val message = when (outcome) {
                                is InvoicePdf.Outcome.Shared -> null
                                is InvoicePdf.Outcome.NotWritten ->
                                    "The invoice could not be written."
                                is InvoicePdf.Outcome.NoReceiver ->
                                    "The invoice was made, but no app on this phone can send a PDF."
                            }
                            message?.let {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = lines.isNotEmpty() && !sharing,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sharing) "Preparing…" else "Create and share")
                }
            }

            Text(
                "The invoice is a PDF. Nothing is recorded: this app keeps no sales.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (previewOpen) {
        PreviewModal(
            viewModel = viewModel,
            data = preview,
            onDismiss = { previewOpen = false },
        )
    }

    if (picking) {
        ItemPickerSheet(
            context = context,
            onPickLine = { line -> viewModel.addLine(line) },
            onDismiss = { picking = false },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this invoice?") },
            text = { Text("The lines are removed. Saved clients are not.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun InvoiceLineRow(
    line: InvoiceLine,
    onSetQuantity: (Int) -> Unit,
    onPriceChange: (Double) -> Unit,
    onDiscountChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        line.lineLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove this line",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QtyStepper(line.quantity, onSetQuantity)
                OutlinedTextField(
                    value = priceText(line.unitPrice),
                    onValueChange = { view ->
                        onPriceChange(view.replace(',', '.').toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Price") },
                    singleLine = true,
                    modifier = Modifier.width(92.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = if (line.lineDiscountPercent == 0) "" else line.lineDiscountPercent.toString(),
                    onValueChange = { view ->
                        onDiscountChange(view.filter(Char::isDigit).take(2).toIntOrNull() ?: 0)
                    },
                    label = { Text("Disc %") },
                    singleLine = true,
                    modifier = Modifier.width(64.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    invoiceMoney(line.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun QtyStepper(
    quantity: Int,
    onQuantity: (Int) -> Unit,
) {
    var typing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onQuantity(quantity - 1) }, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "One fewer",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .width(30.dp)
                .clip(CircleShape)
                .clickable { typing = true }
                .semantics {
                    contentDescription = "$quantity in the invoice. Tap to type a different number"
                },
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onQuantity(quantity + 1) }, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "One more",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (typing) {
        QuantityDialog(
            quantity = quantity,
            onConfirm = {
                typing = false
                onQuantity(it)
            },
            onDismiss = { typing = false },
        )
    }
}

@Composable
private fun QuantityDialog(
    quantity: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("$quantity") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How many items?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()?.coerceAtLeast(1) ?: 1) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyInvoice() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("No items yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Search for one below, or write it in by hand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    onFix: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onFix),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            AssistChip(onClick = onFix, label = { Text("Fill in shop details") })
        }
    }
}

private fun invoiceMoney(value: Double): String =
    String.format(Locale.US, "%.2f", value) + " DH"

private fun priceText(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)

private fun trimPercentForUi(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)

@Composable
private fun DocumentTypeSelector(
    currentType: DocumentType,
    onSelect: (DocumentType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentType.entries.forEach { type ->
            FilterChip(
                selected = type == currentType,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

@Composable
private fun ClientSelector(
    client: InvoiceClient,
    clients: List<InvoiceClient>,
    onChange: (InvoiceClient) -> Unit,
    onOpenManage: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = client.name,
            onValueChange = { onChange(client.copy(name = it)) },
            label = { Text("Client") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedButton(
            onClick = onOpenManage,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Saved clients", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ClientDropdown(
    clients: List<InvoiceClient>,
    onSelect: (InvoiceClient) -> Unit,
    onOpenManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        clients.forEach { saved ->
            DropdownMenuItem(
                text = {
                    Text(
                        saved.name + if (saved.phone.isNotBlank()) "  ·  ${saved.phone}" else "",
                    )
                },
                onClick = { onSelect(saved) },
            )
        }
        if (clients.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No clients saved yet") },
                onClick = onDismiss,
                enabled = false,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Manage clients") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            onClick = onOpenManage,
        )
    }
}

@Composable
private fun SupplierSelector(
    supplier: InvoiceClient?,
    suppliers: List<InvoiceClient>,
    onSelect: (InvoiceClient?) -> Unit,
    onOpenManage: () -> Unit,
) {
    val display = supplier?.name ?: "Select a supplier"
    OutlinedTextField(
        value = display,
        onValueChange = { /* read-only, opened via button */ },
        label = { Text("Supplier") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            OutlinedButton(
                onClick = onOpenManage,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        },
        readOnly = true,
    )
}

@Composable
private fun SupplierDropdown(
    suppliers: List<InvoiceClient>,
    onSelect: (InvoiceClient?) -> Unit,
    onOpenManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        suppliers.forEach { saved ->
            DropdownMenuItem(
                text = {
                    Text(
                        saved.name + if (saved.phone.isNotBlank()) "  ·  ${saved.phone}" else "",
                    )
                },
                onClick = { onSelect(saved) },
            )
        }
        DropdownMenuItem(
            text = { Text("No supplier") },
            onClick = { onSelect(null as InvoiceClient?) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Manage suppliers") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            onClick = onOpenManage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewModal(
    viewModel: InvoiceViewModel,
    data: InvoiceData,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val loading = remember { mutableStateOf(true) }

    LaunchedEffect(data) {
        loading.value = true
        bitmapState.value = null
        val bmp = viewModel.previewBitmap(595)
        bitmapState.value = bmp
        loading.value = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Preview — ${data.header} n° ${data.number}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close preview")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loading.value) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            } else {
                bitmapState.value?.let { bmp ->
                    androidx.compose.ui.graphics.ImageBitmap(bmp)
                }
            }
        }
    }
}
