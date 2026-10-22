package com.yassine.facturemaroc.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceDesignStore
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceNumber
import com.yassine.facturemaroc.data.Backup
import com.yassine.facturemaroc.data.BackupData
import com.yassine.facturemaroc.data.BackupExportOutcome
import com.yassine.facturemaroc.data.BackupReadOutcome
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private const val MAX_LOGO_BYTES = 4 * 1024 * 1024

/**
 * The shop's own details: what appears at the top of every document.
 *
 * These fields are edited once and printed forever, so the screen is arranged
 * like a form on paper — identity, then the legal identifiers, then TVA — and
 * it saves in one action at the bottom rather than writing prefs on every
 * keystroke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailsScreen(
    context: Context,
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.shop.collectAsState()

    var name by remember(settings.name) { mutableStateOf(settings.name) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }
    var ice by remember(settings.ice) { mutableStateOf(settings.ice) }
    var ifNumber by remember(settings.ifNumber) { mutableStateOf(settings.ifNumber) }
    var rc by remember(settings.rc) { mutableStateOf(settings.rc) }
    var patente by remember(settings.patente) { mutableStateOf(settings.patente) }
    var cnss by remember(settings.cnss) { mutableStateOf(settings.cnss) }
    var vatText by remember(settings.vatPercent) {
        mutableStateOf(settings.vatPercent.trimZeros())
    }
    var logoVersion by remember { mutableIntStateOf(0) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val logoFile = remember(context) { InvoiceDesignStore(context).logoFile(context) }
    val logo = remember(logoVersion) {
        if (logoFile.isFile) {
            BitmapFactory.decodeFile(logoFile.absolutePath)?.asImageBitmap()
        } else {
            null
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = readLogoBytes(context, uri)
            when {
                bytes == null || bytes.isEmpty() -> scope.launch {
                    snackbar.showSnackbar("Cette image n'a pas pu être lue.")
                }
                bytes.size > MAX_LOGO_BYTES -> scope.launch {
                    snackbar.showSnackbar("Logo trop lourd : 4 Mo maximum.")
                }
                InvoiceDesignStore(context).saveLogo(context, bytes) -> logoVersion++
                else -> scope.launch {
                    snackbar.showSnackbar("Le logo n'a pas pu être enregistré.")
                }
            }
        }
    }

    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var invoiceCounterText by remember(settings.invoiceCounter) {
        mutableStateOf(settings.invoiceCounter.toString())
    }

    val exportBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (viewModel.exportBackup(uri)) {
                    BackupExportOutcome.Saved ->
                        snackbar.showSnackbar("Sauvegarde exportée.")
                    BackupExportOutcome.Failed ->
                        snackbar.showSnackbar("La sauvegarde n'a pas pu être écrite.")
                }
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val outcome = viewModel.readBackup(uri)) {
                    is BackupReadOutcome.Ready -> pendingRestore = outcome.backup
                    is BackupReadOutcome.Failed -> snackbar.showSnackbar(outcome.message)
                }
            }
        }
    }

    fun save() {
        val vat = vatText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 20.0
        val counter = invoiceCounterText.toIntOrNull()?.coerceAtLeast(1) ?: settings.invoiceCounter
        viewModel.updateShop(
            settings.copy(
                name = name.trim(),
                address = address.trim(),
                ice = ice.trim(),
                ifNumber = ifNumber.trim(),
                rc = rc.trim(),
                patente = patente.trim(),
                cnss = cnss.trim(),
                vatPercent = vat,
                invoiceCounter = counter,
            ),
        )
        InvoiceNumber.rewind(context, DocumentType.FACTURE, counter)
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        "Paramètres du magasin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = { save() }) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Enregistrer les modifications")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "logo") {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(64.dp),
                        ) {
                            if (logo != null) {
                                Image(
                                    bitmap = logo,
                                    contentDescription = "Logo du magasin",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Logo du magasin", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Affiché en haut des factures et devis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(
                                onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(if (logo == null) "Choisir" else "Changer")
                            }
                            if (logo != null) {
                                TextButton(
                                    onClick = {
                                        InvoiceDesignStore(context).removeLogo(context)
                                        logoVersion++
                                    },
                                ) {
                                    Text(
                                        "Retirer",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "identity") {
                SettingsSection(
                    title = "Identité",
                    subtitle = "Ce que le client lit en premier.",
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom du magasin *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Adresse") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }
            }

            item(key = "legal") {
                SettingsSection(
                    title = "Informations légales",
                    subtitle = "Obligatoires sur une facture entre entreprises.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ice,
                            onValueChange = { ice = it },
                            label = { Text("ICE") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = ifNumber,
                            onValueChange = { ifNumber = it },
                            label = { Text("IF") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rc,
                            onValueChange = { rc = it },
                            label = { Text("RC") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = patente,
                            onValueChange = { patente = it },
                            label = { Text("Patente") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = cnss,
                        onValueChange = { cnss = it },
                        label = { Text("CNSS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "vat") {
                SettingsSection(
                    title = "TVA",
                    subtitle = "Appliquée par défaut, après la remise générale.",
                ) {
                    OutlinedTextField(
                        value = vatText,
                        onValueChange = { input ->
                            vatText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Taux de TVA (%)") },
                        singleLine = true,
                        suffix = { Text("%") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        supportingText = {
                            Text("20 % est le taux normal au Maroc. 0 pour une activité exonérée.")
                        },
                    )
                }
            }

            item(key = "backup") {
                SettingsSection(
                    title = "Sauvegarde",
                    subtitle = "Vos données restent sur le téléphone : gardez-en une copie ailleurs.",
                ) {
                    Text(
                        "Le fichier contient le magasin, les clients, les articles, " +
                            "toutes les factures et les compteurs de numérotation. " +
                            "Il peut être restauré sur un autre téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { exportBackup.launch(Backup.fileName()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Exporter une sauvegarde")
                    }
                    OutlinedButton(
                        onClick = {
                            importBackup.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            Icons.Filled.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurer une sauvegarde")
                    }
                }
            }

            item(key = "about") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Numérotation",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Les factures sont numérotées automatiquement et sans trou. " +
                                "Le numéro est pris au moment où vous partagez le PDF, pas à l'ouverture de l'écran.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = invoiceCounterText,
                            onValueChange = { invoiceCounterText = it.filter { it.isDigit() } },
                            label = { Text("Prochain numéro de facture") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            supportingText = {
                                Text(
                                    "Le prochain document partagé portera le numéro ${InvoiceNumber.peek(context, DocumentType.FACTURE)}. " +
                                        "Enregistrer pour appliquer le nouveau compteur.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
        }
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!restoring) pendingRestore = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = {
                Text(
                    "Elle contient ${plural(backup.invoices.size, "document", "documents")}, " +
                        "${plural(backup.clients.size, "client", "clients")} et " +
                        "${plural(backup.items.size, "article", "articles")}.\n\n" +
                        "Les données actuelles de ce téléphone seront remplacées.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            val restored = viewModel.restoreBackup(backup)
                            restoring = false
                            pendingRestore = null
                            if (restored) {
                                logoVersion++
                                snackbar.showSnackbar("Sauvegarde restaurée.")
                            } else {
                                snackbar.showSnackbar("La sauvegarde n'a pas pu être appliquée.")
                            }
                        }
                    },
                ) {
                    Text(if (restoring) "Restauration…" else "Restaurer")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = { pendingRestore = null },
                ) {
                    Text("Annuler")
                }
            },
        )
    }
}

/** "1 document", "2 documents" — and "0 document", which is correct French. */
private fun plural(count: Int, singular: String, plural: String): String =
    if (count > 1) "$count $plural" else "$count $singular"

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Reads a picked image, refusing anything that would not fit a small app. */
private fun readLogoBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_LOGO_BYTES) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}.getOrNull()
