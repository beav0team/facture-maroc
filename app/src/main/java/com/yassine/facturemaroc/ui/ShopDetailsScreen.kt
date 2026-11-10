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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yassine.facturemaroc.data.InvoiceDesignStore
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.CostMethod
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceNumber
import com.yassine.facturemaroc.data.NumberingStore
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.Backup
import com.yassine.facturemaroc.data.BackupData
import com.yassine.facturemaroc.data.BackupExportOutcome
import com.yassine.facturemaroc.data.BackupReadOutcome
import com.yassine.facturemaroc.data.SnapshotStore
import com.yassine.facturemaroc.data.AutoBackup
import com.yassine.facturemaroc.data.StockDrift
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val settings by viewModel.shop.collectAsStateWithLifecycle()

    var name by remember(settings.name) { mutableStateOf(settings.name) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }
    var phone by remember(settings.phone) { mutableStateOf(settings.phone) }
    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var ice by remember(settings.ice) { mutableStateOf(settings.ice) }
    var ifNumber by remember(settings.ifNumber) { mutableStateOf(settings.ifNumber) }
    var rc by remember(settings.rc) { mutableStateOf(settings.rc) }
    var patente by remember(settings.patente) { mutableStateOf(settings.patente) }
    var cnss by remember(settings.cnss) { mutableStateOf(settings.cnss) }
    var bankName by remember(settings.bankName) { mutableStateOf(settings.bankName) }
    var bankRib by remember(settings.bankRib) { mutableStateOf(settings.bankRib) }
    var bankHolder by remember(settings.bankHolder) { mutableStateOf(settings.bankHolder) }
    var lateClause by remember(settings.latePenaltyClause) {
        mutableStateOf(settings.latePenaltyClause)
    }
    var pdfPattern by remember(settings.pdfFileNamePattern) {
        mutableStateOf(settings.pdfFileNamePattern)
    }
    var vatText by remember(settings.vatPercent) {
        mutableStateOf(settings.vatPercent.trimZeros())
    }
    var vatNotApplicable by remember(settings.vatNotApplicable) {
        mutableStateOf(settings.vatNotApplicable)
    }
    var pricesIncludeVat by remember(settings.pricesIncludeVat) {
        mutableStateOf(settings.pricesIncludeVat)
    }
    var showItemBarcode by remember(settings.showItemBarcode) {
        mutableStateOf(settings.showItemBarcode)
    }
    var showItemPurchasePrice by remember(settings.showItemPurchasePrice) {
        mutableStateOf(settings.showItemPurchasePrice)
    }
    var showItemUnit by remember(settings.showItemUnit) {
        mutableStateOf(settings.showItemUnit)
    }
    var showItemStock by remember(settings.showItemStock) {
        mutableStateOf(settings.showItemStock)
    }
    var showItemFolder by remember(settings.showItemFolder) {
        mutableStateOf(settings.showItemFolder)
    }
    var costMethod by remember(settings.costMethod) { mutableStateOf(settings.costMethod) }
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
    var autoSnapshots by remember { mutableStateOf<List<SnapshotStore.Snapshot>>(emptyList()) }
    var autoFolder by remember { mutableStateOf(viewModel.autoBackupFolderLabel()) }
    var snapshotPickerOpen by remember { mutableStateOf(false) }
    val snapshotDate = remember { SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE) }
    LaunchedEffect(Unit) { autoSnapshots = viewModel.autoBackupSnapshots() }
    var restoring by remember { mutableStateOf(false) }
    var archiveReport by remember { mutableStateOf<String?>(null) }
    // The stock check: `null` until the shop asks, then the list of articles
    // whose cache disagrees with the ledger. Kept as a list rather than a
    // message so the repair button exists only when there is something to fix.
    var stockDrift by remember { mutableStateOf<List<StockDrift>?>(null) }
    // The number the sequence really holds, read from the counter that consumes
    // it. Settings used to keep its own copy of this, which nothing ever wrote
    // back, so the field read 1 on every visit.
    val currentCounter = remember(settings) {
        InvoiceNumber.counters(context)[DocumentType.FACTURE.id] ?: 1
    }
    var invoiceCounterText by remember(currentCounter) {
        mutableStateOf(currentCounter.toString())
    }
    // The numbering rules live in their own prefs file; this screen only shows
    // them and writes them on "Enregistrer", like every other field here.
    val numbering = remember(context) { NumberingStore(context) }
    var resetAnnually by remember(numbering) { mutableStateOf(numbering.resetAnnually()) }
    var prefixTexts by remember(numbering) {
        mutableStateOf(DocumentType.entries.associateWith { numbering.prefix(it) })
    }

    val exportBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val outcome = viewModel.exportBackup(uri)) {
                    BackupExportOutcome.Saved ->
                        snackbar.showSnackbar("Sauvegarde exportée.")

                    BackupExportOutcome.SavedWithoutLogo ->
                        snackbar.showSnackbar(
                            "Sauvegarde exportée sans le logo : avec lui, le fichier " +
                                "aurait dépassé la taille que l'application sait relire.",
                        )

                    is BackupExportOutcome.TooLarge ->
                        snackbar.showSnackbar(outcome.message)

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

    val pickBackupFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val kept = viewModel.setAutoBackupFolder(uri)
                autoFolder = viewModel.autoBackupFolderLabel()
                autoSnapshots = viewModel.autoBackupSnapshots()
                snackbar.showSnackbar(
                    if (kept) {
                        "Dossier de sauvegarde enregistré."
                    } else {
                        "Ce dossier ne peut pas être conservé : choisissez un autre emplacement."
                    },
                )
            }
        }
    }

    fun save() {
        val vat = vatText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 20.0
        val counter = invoiceCounterText.toIntOrNull()?.coerceAtLeast(1) ?: currentCounter
        viewModel.updateShop(
            settings.copy(
                name = name.trim(),
                address = address.trim(),
                phone = phone.trim(),
                email = email.trim(),
                ice = ice.trim(),
                ifNumber = ifNumber.trim(),
                rc = rc.trim(),
                patente = patente.trim(),
                cnss = cnss.trim(),
                bankName = bankName.trim(),
                bankRib = bankRib.trim(),
                bankHolder = bankHolder.trim(),
                latePenaltyClause = lateClause.trim(),
                pdfFileNamePattern = pdfPattern.trim().ifBlank { ShopSettings.DEFAULT_PDF_PATTERN },
                vatPercent = vat,
                vatNotApplicable = vatNotApplicable,
                pricesIncludeVat = pricesIncludeVat,
                showItemBarcode = showItemBarcode,
                showItemPurchasePrice = showItemPurchasePrice,
                showItemUnit = showItemUnit,
                showItemStock = showItemStock,
                showItemFolder = showItemFolder,
                costMethod = costMethod,
            ),
        )
        // Only when the shop really asked for a different number. Rewinding on
        // every save is what made "change the logo" reissue F-…-0001 on the
        // next document: the field held a stale 1 and was written back as the
        // counter for the whole sequence.
        if (counter != currentCounter) {
            InvoiceNumber.rewind(context, DocumentType.FACTURE, counter)
        }
        // Prefixes and the annual switch are cheap prefs writes; they are
        // stored even when the rest of the form did not change, because the
        // screen has no other way to know.
        DocumentType.entries.forEach { type ->
            numbering.setPrefix(type, prefixTexts[type].orEmpty())
        }
        numbering.setResetAnnually(resetAnnually)
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
                        modifier = Modifier.semantics { heading() },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Téléphone") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
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
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
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

            item(key = "payment") {
                SettingsSection(
                    title = "Paiement et banque",
                    subtitle = "Ce que le client lit pour vous payer, et ce qui s'imprime en cas de retard.",
                ) {
                    Text(
                        "Coordonnées bancaires",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Banque") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = bankRib,
                        onValueChange = { input ->
                            // Digits and spaces only: a RIB is written in groups of
                            // four, and a letter in it is always a typing mistake.
                            bankRib = input.filter { it.isDigit() || it == ' ' }.take(40)
                        },
                        label = { Text("RIB (24 chiffres)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = bankRib.filter { it.isDigit() }.let {
                            it.isNotEmpty() && it.length != 24
                        },
                        supportingText = {
                            val digits = bankRib.filter { it.isDigit() }
                            Text(
                                if (digits.isNotEmpty() && digits.length != 24) {
                                    "Un RIB marocain compte 24 chiffres : il en reste " +
                                        "${24 - digits.length} à saisir. " +
                                        "Le document s'imprime quand même."
                                } else {
                                    "Imprimé avec les coordonnées de paiement du document."
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = bankHolder,
                        onValueChange = { bankHolder = it },
                        label = { Text("Titulaire du compte") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = lateClause,
                        onValueChange = { lateClause = it },
                        label = { Text("Clause de retard de paiement") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                "Modèle de clause, à faire vérifier par votre comptable " +
                                    "avant de l'imprimer. Elle ne s'imprime que sur les " +
                                    "documents dont le règlement n'est pas comptant. " +
                                    "Videz le champ pour ne rien imprimer.",
                            )
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = pdfPattern,
                        onValueChange = { pdfPattern = it },
                        label = { Text("Nom du fichier PDF") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                "Le nom du fichier partagé. {number}, {client}, {date} " +
                                    "et {type} sont remplacés. L'extension .pdf est ajoutée.",
                            )
                        },
                    )
                }
            }

            item(key = "vat") {
                SettingsSection(
                    title = "TVA",
                    subtitle = "Taux par défaut des nouvelles lignes, après la remise générale.",
                ) {
                    // Morocco has two rates since 1 January 2026; 7 % and 14 %
                    // were removed. Chips because a typo in a free field is a
                    // tax rate on a legal document.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20.0, 10.0, 0.0).forEach { rate ->
                            val selected = vatText.replace(',', '.').toDoubleOrNull() == rate
                            FilterChip(
                                selected = selected,
                                onClick = { vatText = rate.trimZeros() },
                                label = { Text("${rate.trimZeros()} %") },
                            )
                        }
                    }
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
                            Text(
                                "20 % est le taux normal, 10 % le taux réduit. " +
                                    "Les taux de 7 % et 14 % n'existent plus depuis 2026. " +
                                    "Chaque ligne peut avoir son propre taux.",
                            )
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = vatNotApplicable,
                            onCheckedChange = { vatNotApplicable = it },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Auto-entrepreneur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "TVA non applicable sur les documents de ce magasin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Vos prix sont affichés",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = "HT — TVA en sus",
                            selected = !pricesIncludeVat,
                            onClick = { pricesIncludeVat = false },
                        )
                        ChoiceChip(
                            label = "TTC — TVA incluse",
                            selected = pricesIncludeVat,
                            onClick = { pricesIncludeVat = true },
                        )
                    }
                    Text(
                        if (pricesIncludeVat) {
                            "Vous tapez le prix que le client paie : l'application en déduit " +
                                "le HT et la TVA. La facture montre toujours HT + TVA, comme " +
                                "l'exige une facture."
                        } else {
                            "Vous tapez le prix hors taxe : la TVA s'ajoute au total."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "articles") {
                SettingsSection(
                    title = "Articles",
                    subtitle = "Choisissez ce que la fiche article vous demande.",
                ) {
                    Text(
                        "Décocher un champ ne l'efface pas : la valeur reste sur " +
                            "l'article et revient si vous le réactivez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FieldSwitch(
                        title = "Code-barres",
                        description = "Le champ et le bouton scanner dans la fiche article.",
                        checked = showItemBarcode,
                        onCheckedChange = { showItemBarcode = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FieldSwitch(
                        title = "Prix d'achat",
                        description = "Le champ, et la ligne « Marge » qui en dépend.",
                        checked = showItemPurchasePrice,
                        onCheckedChange = { showItemPurchasePrice = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FieldSwitch(
                        title = "Unité",
                        description = "Heure, kg, m²… sous la désignation de l'article.",
                        checked = showItemUnit,
                        onCheckedChange = { showItemUnit = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FieldSwitch(
                        title = "Stock",
                        description = "La quantité dans la fiche, la ligne « en stock » sur " +
                            "la carte, et la distinction prestation / produit.",
                        checked = showItemStock,
                        onCheckedChange = { showItemStock = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FieldSwitch(
                        title = "Dossier",
                        description = "Le sélecteur de dossier, les filtres de l'onglet " +
                            "Articles et le chemin sous la désignation.",
                        checked = showItemFolder,
                        onCheckedChange = { showItemFolder = it },
                    )
                }
            }

            item(key = "valuation") {
                SettingsSection(
                    title = "Valorisation du stock",
                    subtitle = "Comment l'application calcule ce que vaut un article sur l'étagère.",
                ) {
                    Text(
                        "Le choix ne modifie que les mouvements à venir : un document " +
                            "déjà émis garde le coût figé sur chacune de ses lignes, " +
                            "et changer de méthode ne réécrit jamais une facture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CostMethod.entries.forEach { method ->
                        ChoiceChip(
                            label = method.label,
                            selected = costMethod == method,
                            onClick = { costMethod = method },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        costMethod.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            item(key = "auto-backup") {
                SettingsSection(
                    title = "Sauvegarde automatique",
                    subtitle = "Une copie par jour, sans y penser.",
                ) {
                    Text(
                        "L'application écrit une copie d'elle-même au premier lancement " +
                            "de la journée, et en garde " + AutoBackup.KEEP + " sur le " +
                            "téléphone. Elle survit à une facture effacée ou à un fichier " +
                            "devenu illisible ; choisir un dossier y ajoute une copie hors " +
                            "du téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        autoSnapshots.firstOrNull()?.let { last ->
                            "Dernière copie : ${snapshotDate.format(Date(last.takenAt))} · " +
                                "${autoSnapshots.size} copie${if (autoSnapshots.size > 1) "s" else ""} gardée${if (autoSnapshots.size > 1) "s" else ""}."
                        } ?: "Aucune copie automatique pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = viewModel.runAutoBackup()
                                autoSnapshots = viewModel.autoBackupSnapshots()
                                snackbar.showSnackbar(
                                    if (ok) "Copie automatique créée." else "La copie n'a pas pu être écrite.",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Créer une copie maintenant")
                    }
                    OutlinedButton(
                        onClick = { pickBackupFolder.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(autoFolder?.let { "Dossier : $it" } ?: "Choisir un dossier hors du téléphone")
                    }
                    if (autoFolder != null) {
                        TextButton(
                            onClick = {
                                viewModel.forgetAutoBackupFolder()
                                autoFolder = null
                                scope.launch { snackbar.showSnackbar("Dossier de sauvegarde retiré.") }
                            },
                        ) {
                            Text("Retirer le dossier")
                        }
                    }
                    OutlinedButton(
                        onClick = { snapshotPickerOpen = true },
                        enabled = autoSnapshots.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurer une copie automatique")
                    }
                }
            }

            item(key = "archive") {
                SettingsSection(
                    title = "Archive des documents",
                    subtitle = "Ce qui est réellement parti, et sa vérification.",
                ) {
                    Text(
                        "Chaque PDF partagé et chaque XML exporté est recopié ici, " +
                            "nommé par l'empreinte de son contenu. La vérification " +
                            "recalcule la chaîne : un fichier remplacé ou une entrée " +
                            "modifiée est signalé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    archiveReport?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                archiveReport = kotlinx.coroutines.withContext(
                                    kotlinx.coroutines.Dispatchers.IO,
                                ) {
                                    viewModel.archive.verify().message
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Vérifier l'archive")
                    }
                }
            }

            item(key = "stock") {
                SettingsSection(
                    title = "Stock et journal",
                    subtitle = "Chaque mouvement est conservé ; la quantité affichée se recalcule.",
                ) {
                    Text(
                        "Les ventes, avoirs, achats et inventaires sont écrits dans un " +
                            "journal, et la quantité affichée n'en est qu'un raccourci. " +
                            "La vérification compare le raccourci au journal : elle ne " +
                            "corrige rien toute seule, elle montre les articles qui " +
                            "divergent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stockDrift?.let { drift ->
                        if (drift.isEmpty()) {
                            Text(
                                "Aucun écart : les quantités affichées correspondent au journal.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                if (drift.size == 1) {
                                    "1 article diverge du journal :"
                                } else {
                                    "${drift.size} articles divergent du journal :"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            // Five is enough to recognise the problem; the count
                            // above says how many there really are.
                            drift.take(5).forEach { row ->
                                Text(
                                    "${row.item.label} : affiché ${row.cached}, " +
                                        "journal ${row.ledger}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (drift.size > 5) {
                                Text(
                                    "et ${drift.size - 5} autre(s)…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                stockDrift = viewModel.stockDrift()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Vérifier le stock")
                    }
                    if ((stockDrift?.size ?: 0) > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val repaired = viewModel.repairStockCache()
                                    stockDrift = emptyList()
                                    snackbar.showSnackbar(
                                        if (repaired == 1) {
                                            "1 quantité recalculée depuis le journal."
                                        } else {
                                            "$repaired quantités recalculées depuis le journal."
                                        },
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Recalculer depuis le journal")
                        }
                    }
                }
            }

            item(key = "numbering") {
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
                                    "Numéro que portera le prochain document partagé. " +
                                        "Ne le changez que pour repartir d'un autre numéro : " +
                                        "les documents déjà envoyés ne sont pas renumérotés.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Repartir de 1 chaque année",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "Au 1er janvier, la séquence de chaque type recommence à 0001. " +
                                        "Les documents déjà émis gardent leur numéro.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = resetAnnually,
                                onCheckedChange = { resetAnnually = it },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Préfixe par type de document",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Lettres, chiffres, tiret et tiret bas. Laissez vide pour " +
                                "revenir au préfixe d'origine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Two per row keeps ten types a short block; a full
                        // list of text fields would push the backup section
                        // off the screen.
                        DocumentType.entries.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { type ->
                                    OutlinedTextField(
                                        value = prefixTexts[type].orEmpty(),
                                        onValueChange = { input ->
                                            val clean = input
                                                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                                                .take(12)
                                            prefixTexts = prefixTexts + (type to clean)
                                        },
                                        label = { Text(type.label) },
                                        placeholder = { Text(type.prefix) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next,
                                        ),
                                    )
                                }
                                // Keep the last row's single field the same
                                // width as the ones above it.
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (snapshotPickerOpen) {
        AlertDialog(
            onDismissRequest = { snapshotPickerOpen = false },
            title = { Text("Copies automatiques") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    autoSnapshots.forEach { snapshot ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    snapshotPickerOpen = false
                                    scope.launch {
                                        when (val outcome = viewModel.readSnapshot(snapshot)) {
                                            is BackupReadOutcome.Ready -> pendingRestore = outcome.backup
                                            is BackupReadOutcome.Failed ->
                                                snackbar.showSnackbar(outcome.message)
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    snapshotDate.format(Date(snapshot.takenAt)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (snapshot.bytes < 1024) {
                                        "${snapshot.bytes} o"
                                    } else {
                                        "${snapshot.bytes / 1024} Ko"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { snapshotPickerOpen = false }) { Text("Annuler") }
            },
        )
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

/**
 * One article field the shop can switch off.
 *
 * The switch sits left of its label, the way the TVA one does, so the row reads
 * as "this is on/off" before it reads as a sentence about fields.
 */
@Composable
private fun FieldSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
