package com.yassine.facturemaroc.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceRecord
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore
import com.yassine.facturemaroc.data.StockMovement
import com.yassine.facturemaroc.data.XmlExportOutcome
import com.yassine.facturemaroc.data.billedInMonth
import com.yassine.facturemaroc.invoice.InvoiceXml
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val label: String) {
    INVOICES("Factures"),
    CLIENTS("Clients"),
    ARTICLES("Articles"),
}

/**
 * The screen the app opens on: what has been invoiced, who it was for, and
 * what the shop sells by heart.
 *
 * The three tabs are the whole app in one place. Before this, clients and
 * saved items were only reachable from inside a half-written invoice, which
 * meant they could not be prepared ahead of time and could not be corrected
 * afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InvoiceViewModel,
    onNewInvoice: (InvoiceClient?) -> Unit,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val records by viewModel.invoices.invoices.collectAsState()
    val clients by viewModel.clients.clients.collectAsState()
    val items by viewModel.savedItems.items.collectAsState()
    val folders by viewModel.savedItems.folders.collectAsState()
    val shop by viewModel.shop.collectAsState()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val tab = HomeTab.entries[tabIndex]

    var clientEditorOpen by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var itemEditorOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SavedItem?>(null) }
    /*
     * Where the Articles tab is standing.
     *
     * `null` is the folder grid. `""` is the articles that belong to no folder,
     * and any other value is a folder name. The grid's "Tous" tile is a second
     * flag rather than a sentinel name, because a shop can create a folder
     * called anything and a name it is allowed to type must never mean
     * "everything".
     */
    var inFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllArticles by rememberSaveable { mutableStateOf(false) }
    var folderEditorOpen by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var deleteFolder by remember { mutableStateOf<String?>(null) }
    var detailRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var xmlRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteInvoice by remember { mutableStateOf<InvoiceRecord?>(null) }
    var deleteClient by remember { mutableStateOf<InvoiceClient?>(null) }
    var deleteItem by remember { mutableStateOf<SavedItem?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val xmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri: Uri? ->
        val record = xmlRecord
        xmlRecord = null
        if (uri != null && record != null) {
            scope.launch {
                val message = when (viewModel.exportRecordXml(record, uri)) {
                    XmlExportOutcome.Saved ->
                        "Fichier XML enregistré : ${record.data.number}"
                    XmlExportOutcome.Failed ->
                        "Le fichier XML n'a pas pu être enregistré."
                    XmlExportOutcome.NothingToExport ->
                        "Ce document n'a pas de lignes à exporter."
                }
                snackbar.showSnackbar(message)
            }
        }
    }

    fun exportXml(record: InvoiceRecord) {
        xmlRecord = record
        xmlLauncher.launch(InvoiceXml.fileName(record.data))
    }

    val fabLabel = when (tab) {
        HomeTab.INVOICES -> "Nouvelle facture"
        HomeTab.CLIENTS -> "Nouveau client"
        HomeTab.ARTICLES -> "Nouvel article"
    }

    /*
     * The Articles tab is a stack — the folder grid, then one folder at each
     * depth — and both the top bar and the tab content read from these, so the
     * title can never disagree with the list about which folder is open.
     *
     * `null` is the grid, `""` is "Sans dossier", and anything else is a folder
     * path such as "Pneus/Hiver".
     */
    val articleFolder = inFolder
    val articlesInList = tab == HomeTab.ARTICLES && (showAllArticles || articleFolder != null)
    val subFolders = remember(folders, articleFolder) {
        articleFolder?.let { SavedItemStore.children(it, folders) }.orEmpty()
    }
    val articleScopeItems = when {
        showAllArticles || articleFolder == null -> items
        else -> SavedItemStore.itemsIn(articleFolder, items)
    }

    // Up one level, not out of the app: the folder grid, then each parent, so
    // the system back gesture walks the same tree the tiles do.
    val goUp: () -> Unit = {
        if (showAllArticles) {
            showAllArticles = false
        } else {
            val path = inFolder
            inFolder = when {
                path.isNullOrEmpty() -> null
                path.contains(SavedItemStore.SEPARATOR) -> SavedItemStore.parentOf(path)
                else -> null
            }
        }
    }
    BackHandler(enabled = articlesInList, onBack = goUp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (articlesInList) {
                        Column {
                            Text(
                                when {
                                    showAllArticles -> "Tous les articles"
                                    articleFolder?.isEmpty() == true -> "Sans dossier"
                                    // The whole path, because a sub-folder called
                                    // "Hiver" says nothing about where it is, and
                                    // the shop may have two of them.
                                    else -> SavedItemStore.display(articleFolder.orEmpty())
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                countLine(articleScopeItems.size, subFolders.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column {
                            Text(
                                "Facture Maroc",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                shop.name.ifBlank { "Gestion de facturation" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (articlesInList) {
                        IconButton(onClick = goUp) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = if (articleFolder.orEmpty().contains(SavedItemStore.SEPARATOR)) {
                                    "Dossier parent"
                                } else {
                                    "Retour aux dossiers"
                                },
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                HomeTab.entries.forEach { candidate ->
                    NavigationBarItem(
                        selected = candidate == tab,
                        onClick = {
                            tabIndex = candidate.ordinal
                            query = ""
                        },
                        icon = {
                            Icon(
                                when (candidate) {
                                    HomeTab.INVOICES -> Icons.Filled.ReceiptLong
                                    HomeTab.CLIENTS -> Icons.Filled.People
                                    HomeTab.ARTICLES -> Icons.Filled.Inventory2
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(candidate.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        HomeTab.INVOICES -> onNewInvoice(null)
                        HomeTab.CLIENTS -> {
                            editingClient = null
                            clientEditorOpen = true
                        }
                        HomeTab.ARTICLES -> {
                            editingItem = null
                            itemEditorOpen = true
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Add, contentDescription = fabLabel)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = when (tab) {
                    HomeTab.INVOICES -> "Client, numéro ou date"
                    HomeTab.CLIENTS -> "Nom, téléphone ou ICE"
                    HomeTab.ARTICLES -> "Rechercher un article"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when (tab) {
                HomeTab.INVOICES -> InvoicesTab(
                    records = records,
                    query = query,
                    onOpenRecord = onOpenRecord,
                    onShowDetail = { detailRecord = it },
                    onShare = { record ->
                        scope.launch {
                            val outcome = viewModel.shareRecord(record)
                            snackbar.showSnackbar(outcome.shareMessage())
                        }
                    },
                    onExportXml = ::exportXml,
                    onDuplicate = onOpenRecord,
                    onDelete = { deleteInvoice = it },
                )

                HomeTab.CLIENTS -> ClientsTab(
                    clients = clients,
                    records = records,
                    query = query,
                    onEdit = {
                        editingClient = it
                        clientEditorOpen = true
                    },
                    onInvoice = { onNewInvoice(it) },
                    onDelete = { deleteClient = it },
                )

                HomeTab.ARTICLES -> {
                    /*
                     * A search typed on the folder grid looks through the whole
                     * catalogue, because from there the shop is asking "where
                     * did I put this?". Typed inside a folder it looks through
                     * that folder and everything below it, because there the
                     * question is "is it here?" and a tyre filed under
                     * Pneus/Hiver is still in Pneus.
                     */
                    val searching = query.isNotBlank()
                    val searchingEverything = searching && !articlesInList
                    when {
                        searching -> {
                            val scopeItems = if (searchingEverything) {
                                items
                            } else {
                                SavedItemStore.itemsIn(articleFolder.orEmpty(), items, deep = true)
                            }
                            ArticlesList(
                                // One search rule for the whole app, the same
                                // one the invoice picker uses, barcode included.
                                items = remember(scopeItems, query) {
                                    scopeItems.filter { SavedItemStore.matches(it, query) }
                                },
                                // A result can come from a sub-folder, and an
                                // article the shop cannot place is an article it
                                // will not find again.
                                showFolder = searchingEverything || showAllArticles || subFolders.isNotEmpty(),
                                emptyTitle = "Aucun résultat",
                                emptyMessage = "Essayez un autre mot.",
                                onEdit = {
                                    editingItem = it
                                    itemEditorOpen = true
                                },
                                onDelete = { deleteItem = it },
                            )
                        }

                        articlesInList -> {
                            FolderContents(
                                articles = articleScopeItems,
                                subFolders = subFolders,
                                allItems = items,
                                onOpenFolder = { path ->
                                    inFolder = path
                                    query = ""
                                },
                                onCreateFolder = {
                                    renamingFolder = null
                                    folderEditorOpen = true
                                },
                                onRenameFolder = {
                                    renamingFolder = it
                                    folderEditorOpen = true
                                },
                                onDeleteFolder = { deleteFolder = it },
                                onEdit = {
                                    editingItem = it
                                    itemEditorOpen = true
                                },
                                onDelete = { deleteItem = it },
                            )
                        }

                        else -> {
                            ArticlesFoldersGrid(
                                items = items,
                                folders = folders,
                                onOpenAll = { showAllArticles = true },
                                onOpenFolder = { path ->
                                    inFolder = path
                                    query = ""
                                },
                                onCreateFolder = {
                                    renamingFolder = null
                                    folderEditorOpen = true
                                },
                                onRenameFolder = {
                                    renamingFolder = it
                                    folderEditorOpen = true
                                },
                                onDeleteFolder = { deleteFolder = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (clientEditorOpen) {
        ClientEditorDialog(
            initial = editingClient,
            onDismiss = { clientEditorOpen = false },
            onSave = { updated ->
                val previous = editingClient
                if (previous != null && !previous.name.equals(updated.name, ignoreCase = true)) {
                    viewModel.clients.delete(previous)
                }
                viewModel.clients.save(updated)
                clientEditorOpen = false
            },
        )
    }

    if (itemEditorOpen) {
        ItemEditorDialog(
            initial = editingItem,
            folders = folders,
            defaultFolder = if (showAllArticles) "" else inFolder.orEmpty(),
            // The sale price is asked for in the basis the shop quotes, so the
            // number typed here is the number the customer is told.
            pricesIncludeVat = shop.pricesIncludeVat,
            vatPercent = if (shop.vatNotApplicable) 0.0 else shop.vatPercent,
            onDismiss = { itemEditorOpen = false },
            onSave = { updated ->
                val previous = editingItem
                // The label is the catalogue's key. Saving a "new" article under
                // a name that is already taken would replace that product —
                // including the quantity on its shelf — with a fresh row at
                // zero. Better to say so and let the shop open the real one.
                val clash = previous == null && items.any {
                    it.label.trim().equals(updated.label.trim(), ignoreCase = true)
                }
                if (clash) {
                    scope.launch {
                        snackbar.showSnackbar(
                            "« ${updated.label.trim()} » existe déjà. Ouvrez-le pour le modifier.",
                        )
                    }
                } else {
                    if (previous != null && !previous.label.equals(updated.label, ignoreCase = true)) {
                        viewModel.savedItems.delete(previous)
                    }
                    viewModel.savedItems.save(updated)
                    itemEditorOpen = false
                }
            },
        )
    }

    detailRecord?.let { record ->
        InvoiceDetailSheet(
            record = record,
            onDismiss = { detailRecord = null },
            onShare = {
                detailRecord = null
                scope.launch {
                    val outcome = viewModel.shareRecord(record)
                    snackbar.showSnackbar(outcome.shareMessage())
                }
            },
            onExportXml = {
                detailRecord = null
                exportXml(record)
            },
            onDuplicate = {
                detailRecord = null
                onOpenRecord(record)
            },
            onDelete = {
                detailRecord = null
                deleteInvoice = record
            },
        )
    }

    deleteInvoice?.let { record ->
        // What deletion does now depends on what the document was, so the
        // sentence has to match. Telling a shop "the line only disappears" and
        // then quietly adding a tyre back to its shelf is how an app loses
        // trust over a dialog nobody rereads.
        val effect = when {
            record.isDraft -> "Le brouillon disparaît de l'historique."
            record.stockApplied && StockMovement.direction(record.data.documentType) > 0 ->
                "Le PDF déjà envoyé reste chez le client ; les quantités rendues par l'avoir sont reprises du stock."
            record.stockApplied ->
                "Le PDF déjà envoyé reste chez le client ; les quantités vendues retournent au stock."
            else -> "Le PDF déjà envoyé reste chez le client ; la ligne disparaît de l'historique."
        }
        ConfirmDeleteDialog(
            title = if (record.isDraft) "Supprimer ce brouillon ?" else "Supprimer ce document ?",
            message = "${record.data.number} · ${record.title} — ${record.data.total.asDirhams()}. $effect",
            onDismiss = { deleteInvoice = null },
            onConfirm = {
                viewModel.deleteRecord(record.id)
                deleteInvoice = null
                scope.launch {
                    snackbar.showSnackbar(
                        if (record.stockApplied) {
                            "Document supprimé, stock rétabli."
                        } else {
                            "Document supprimé de l'historique."
                        },
                    )
                }
            },
        )
    }

    deleteClient?.let { client ->
        DeleteSimpleDialog(
            title = "Supprimer ce client ?",
            message = "${client.name} disparaîtra de la liste. Les factures déjà émises gardent ses coordonnées.",
            onDismiss = { deleteClient = null },
            onConfirm = {
                viewModel.clients.delete(client)
                deleteClient = null
            },
        )
    }

    deleteItem?.let { item ->
        DeleteSimpleDialog(
            title = "Supprimer cet article ?",
            message = "${item.label} ne sera plus proposé sur les prochaines factures. Les lignes déjà écrites ne changent pas.",
            onDismiss = { deleteItem = null },
            onConfirm = {
                viewModel.savedItems.delete(item)
                deleteItem = null
            },
        )
    }

    if (folderEditorOpen) {
        val previous = renamingFolder
        FolderEditorDialog(
            initial = previous?.let { SavedItemStore.nameOf(it) },
            parent = if (previous == null) inFolder.orEmpty() else SavedItemStore.parentOf(previous),
            onSave = { name ->
                if (previous == null) {
                    val created = viewModel.savedItems.createFolder(name, inFolder.orEmpty())
                    if (created != null) {
                        // A folder made while standing in another one opens, so
                        // the shop can put something in it straight away.
                        inFolder = created
                        folderEditorOpen = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Ce dossier existe déjà ici.") }
                    }
                } else {
                    val renamed = viewModel.savedItems.renameFolder(previous, name)
                    if (renamed != null) {
                        // Renaming the folder that is open (or one above it)
                        // keeps the shop where it was standing instead of
                        // dropping it back on the grid.
                        inFolder?.let { open ->
                            if (SavedItemStore.isIn(open, previous)) {
                                inFolder = SavedItemStore.rebase(open, previous, renamed)
                            }
                        }
                        folderEditorOpen = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Ce nom est déjà utilisé ici.") }
                    }
                }
            },
            onDismiss = { folderEditorOpen = false },
        )
    }

    deleteFolder?.let { folder ->
        val inSubtree = items.count { SavedItemStore.isIn(it.folder, folder) }
        val subs = folders.count { it != folder && SavedItemStore.isIn(it, folder) }
        val parent = SavedItemStore.parentOf(folder)
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Supprimer ce dossier ?") },
            text = {
                Text(
                    listOf(
                        "« ${SavedItemStore.nameOf(folder)} » sera supprimé" +
                            if (subs == 0) {
                                "."
                            } else {
                                " avec ${if (subs == 1) "son sous-dossier" else "ses $subs sous-dossiers"}."
                            },
                        when (inSubtree) {
                            0 -> "Aucun article n'y est rangé."
                            1 -> "1 article sera conservé dans « ${folderLabel(parent)} »."
                            else -> "$inSubtree articles seront conservés dans « ${folderLabel(parent)} »."
                        },
                    ).joinToString(" "),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savedItems.deleteFolder(folder)
                        // Deleting the folder that is open, or one above it,
                        // would leave the tab standing on a name that no longer
                        // exists.
                        inFolder?.let { open ->
                            if (SavedItemStore.isIn(open, folder)) inFolder = parent.ifEmpty { null }
                        }
                        deleteFolder = null
                    },
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) { Text("Annuler") }
            },
        )
    }
}

/** "Sans dossier" for the root, the folder's own name otherwise. */
private fun folderLabel(path: String): String =
    if (path.isEmpty()) "Sans dossier" else SavedItemStore.nameOf(path)

@Composable
private fun DeleteSimpleDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun InvoicesTab(
    records: List<InvoiceRecord>,
    query: String,
    onOpenRecord: (InvoiceRecord) -> Unit,
    onShowDetail: (InvoiceRecord) -> Unit,
    onShare: (InvoiceRecord) -> Unit,
    onExportXml: (InvoiceRecord) -> Unit,
    onDuplicate: (InvoiceRecord) -> Unit,
    onDelete: (InvoiceRecord) -> Unit,
) {
    val filtered = remember(records, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) records else records.filter { record ->
            listOf(
                record.title,
                record.data.number,
                record.data.date,
                record.data.total.toString(),
            ).any { it.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.ReceiptLong,
            title = if (records.isEmpty()) "Aucune facture pour l'instant" else "Aucun résultat",
            message = if (records.isEmpty()) {
                "Appuyez sur + pour créer votre premier document. " +
                    "Il sera gardé ici, brouillon puis facture émise."
            } else {
                "Essayez un nom de client, un numéro ou une date."
            },
        )
        return
    }

    val draftCount = filtered.count { it.isDraft }
    val issuedCount = filtered.size - draftCount

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (query.isBlank()) {
            item(key = "summary") {
                MonthSummary(records = records)
            }
        }

        if (draftCount > 0) {
            item(key = "drafts-heading") {
                SectionHeading("Brouillons", trailing = "$draftCount en attente")
            }
            items(filtered.filter { it.isDraft }, key = { "draft-${it.id}" }) { record ->
                InvoiceCard(
                    record = record,
                    onClick = { onOpenRecord(record) },
                    onShare = { onShare(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onDelete = { onDelete(record) },
                )
            }
        }

        if (issuedCount > 0) {
            item(key = "issued-heading") {
                SectionHeading("Documents émis", trailing = "$issuedCount")
            }
            items(filtered.filterNot { it.isDraft }, key = { "issued-${it.id}" }) { record ->
                InvoiceCard(
                    record = record,
                    onClick = { onShowDetail(record) },
                    onShare = { onShare(record) },
                    onExportXml = { onExportXml(record) },
                    onDuplicate = { onDuplicate(record) },
                    onDelete = { onDelete(record) },
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(records: List<InvoiceRecord>) {
    val monthStamp = remember {
        SimpleDateFormat("MM/yyyy", Locale.FRANCE).format(Date())
    }
    val issuedThisMonth = remember(records, monthStamp) {
        records.billedInMonth(monthStamp)
    }
    val total = issuedThisMonth.sumOf { it.data.total }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Facturé ce mois-ci",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                total.asDirhams(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when (issuedThisMonth.size) {
                        0 -> "Aucune facture émise ce mois-ci"
                        1 -> "1 facture émise ce mois-ci"
                        else -> "${issuedThisMonth.size} factures émises ce mois-ci"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    record: InvoiceRecord,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val draft = record.isDraft

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (draft) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (draft) Icons.Filled.Edit else Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = if (draft) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    record.data.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.data.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    record.data.total.asDirhams(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = record.status.label,
                    container = if (draft) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    content = if (draft) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (draft) "Reprendre le brouillon" else "Ouvrir") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    if (!draft) {
                        DropdownMenuItem(
                            text = { Text("Partager le PDF") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Exporter en XML") },
                            leadingIcon = {
                                Icon(Icons.Filled.Description, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onExportXml()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Dupliquer") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientsTab(
    clients: List<InvoiceClient>,
    records: List<InvoiceRecord>,
    query: String,
    onEdit: (InvoiceClient) -> Unit,
    onInvoice: (InvoiceClient) -> Unit,
    onDelete: (InvoiceClient) -> Unit,
) {
    val filtered = remember(clients, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) clients else clients.filter {
            listOf(it.name, it.phone, it.email, it.ice).any { field -> field.lowercase().contains(q) }
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = if (clients.isEmpty()) "Aucun client enregistré" else "Aucun résultat",
            message = if (clients.isEmpty()) {
                "Enregistrez vos clients une fois. Sur chaque facture, leur nom, ICE et adresse " +
                    "se remplissent sans les retaper."
            } else {
                "Essayez un autre nom, un téléphone ou un ICE."
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "count") {
            SectionHeading("Clients enregistrés", trailing = "${filtered.size}")
        }
        items(filtered, key = { "client-${it.name.lowercase()}" }) { client ->
            ClientCard(
                client = client,
                documentCount = records.count {
                    !it.isDraft && it.data.client.name.equals(client.name, ignoreCase = true)
                },
                onClick = { onEdit(client) },
                onInvoice = { onInvoice(client) },
                onDelete = { onDelete(client) },
            )
        }
    }
}

@Composable
private fun ClientCard(
    client: InvoiceClient,
    documentCount: Int,
    onClick: () -> Unit,
    onInvoice: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val initials = remember(client.name) {
        client.name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (documentCount > 0) {
                Text(
                    if (documentCount == 1) "1 document" else "$documentCount documents",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Facturer ce client") },
                        leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onInvoice()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The line under the Articles title: what is on this level, not below it.
 *
 * "2 articles · 1 sous-dossier" is honest about a folder whose shelves hold
 * more, because the tiles underneath say how much each of those holds.
 */
private fun countLine(articles: Int, folders: Int): String {
    val items = when (articles) {
        0 -> "Aucun article"
        1 -> "1 article"
        else -> "$articles articles"
    }
    if (folders == 0) return items
    return "$items · $folders sous-dossier${if (folders > 1) "s" else ""}"
}

/**
 * The Articles tab at rest: the folders, not the articles.
 *
 * A shop with a hundred articles cannot scan a hundred rows for "Montage", so
 * the folders come first the way a file manager does it, and the list behind a
 * tile is short by construction.
 *
 * "Tous" is a tile here rather than a filter chip. A chip would sit above the
 * folders and change what is drawn beneath it; a tile is somewhere the shop
 * goes and comes back from, which is what a folder and a full list have in
 * common.
 */
@Composable
private fun ArticlesFoldersGrid(
    items: List<SavedItem>,
    folders: List<String>,
    onOpenAll: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    if (items.isEmpty() && folders.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Inventory2,
            title = "Aucun article enregistré",
            message = "Ajoutez ce que vous vendez souvent — une prestation, un produit, une pièce — " +
                "et retrouvez-le en un geste sur chaque facture.",
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "all") {
            FolderTile(
                title = "Tous",
                count = items.size,
                icon = Icons.Filled.Inventory2,
                onClick = onOpenAll,
            )
        }
        item(key = "unfiled") {
            FolderTile(
                title = "Sans dossier",
                count = items.count { it.folder.isBlank() },
                icon = Icons.Filled.Inbox,
                onClick = { onOpenFolder("") },
            )
        }
        gridItems(folders.filter { SavedItemStore.parentOf(it).isEmpty() }, key = { "folder-$it" }) { folder ->
            FolderTile(
                title = folder,
                // Everything the shelf holds, sub-shelves included: "Pneus"
                // that says 2 while 3 more tyres are filed under Pneus/Hiver
                // would read as an inventory that lost stock.
                count = SavedItemStore.itemsIn(folder, items, deep = true).size,
                icon = Icons.Filled.Folder,
                onClick = { onOpenFolder(folder) },
                onRename = { onRenameFolder(folder) },
                onDelete = { onDeleteFolder(folder) },
            )
        }
        item(key = "new-folder") {
            NewFolderTile(onCreateFolder)
        }
    }
}

/**
 * One open folder: the shelves inside it first, then what sits on it directly.
 *
 * Both are drawn on one scrolling surface by one grid, because they are one
 * place: walking into "Pneus" and seeing "Hiver" above the tyres filed
 * straight there is how a cupboard looks. A second list would need a second
 * scroll position and a sentence explaining that it is not the whole folder.
 *
 * Sub-folder tiles carry everything inside them, sub-shelves included, while
 * the title above counts what is on this level — a tile that said "2" while
 * three more tyres sat in Pneus/Hiver would read as inventory that vanished.
 */
@Composable
private fun FolderContents(
    articles: List<SavedItem>,
    subFolders: List<String>,
    allItems: List<SavedItem>,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onEdit: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(subFolders, key = { "sub-$it" }) { path ->
            FolderTile(
                title = SavedItemStore.nameOf(path),
                count = SavedItemStore.itemsIn(path, allItems, deep = true).size,
                icon = Icons.Filled.Folder,
                onClick = { onOpenFolder(path) },
                onRename = { onRenameFolder(path) },
                onDelete = { onDeleteFolder(path) },
            )
        }
        item(key = "new-subfolder") {
            NewFolderTile(onCreateFolder, label = "Nouveau sous-dossier")
        }
        if (articles.isNotEmpty()) {
            item(key = "articles-heading", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(
                    "Articles",
                    trailing = "${articles.size} ici",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            item(key = "no-articles", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Aucun article directement dans ce dossier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // The alias, because `items` inside a grid scope is the count-based
        // overload and would take the list for a number of blank cells.
        gridItems(
            articles,
            key = { "item-${it.label.lowercase()}" },
            span = { GridItemSpan(maxLineSpan) },
        ) { item ->
            ItemCard(
                item = item,
                showFolder = false,
                onClick = { onEdit(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

/**
 * One square in the folder grid.
 *
 * Rename and delete live on the tile itself, in a small overflow menu, rather
 * than in a separate "manage folders" dialog. The action belongs to the folder
 * it acts on, and a dialog listing every folder in order to edit one of them
 * was a second copy of this grid with different controls.
 */
@Composable
private fun FolderTile(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // The name sits at the bottom of the tile, the way a file
                // manager draws it: the icon is what the eye finds, the label is
                // what confirms it, and the gap between them is what makes a
                // grid of folders scan faster than a list of rows.
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
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
            }
            if (onRename != null && onDelete != null) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Actions sur « $title »",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Renommer") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The "Nouveau dossier" square that closes the grid. */
@Composable
private fun NewFolderTile(onClick: () -> Unit, label: String = "Nouveau dossier") {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One folder's articles, or the whole catalogue.
 *
 * The heading lives in the app bar now, so this is only the rows: the folder
 * grid cannot be reached from here by accident, and leaving is the back arrow
 * the app bar owns, which the system back gesture also triggers.
 */
@Composable
private fun ArticlesList(
    /** Already filtered by [SavedItemStore.matches]; this only draws them. */
    items: List<SavedItem>,
    showFolder: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onEdit: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            EmptyState(
                icon = Icons.Filled.Inventory2,
                title = emptyTitle,
                message = emptyMessage,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { "item-${it.label.lowercase()}" }) { item ->
            ItemCard(
                item = item,
                showFolder = showFolder,
                onClick = { onEdit(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    /** False inside a folder, where repeating its name on every row is noise. */
    showFolder: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = listOf(
        if (showFolder) SavedItemStore.display(item.folder) else "",
        item.unit,
    )
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.price.asDirhams(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                // A quantity is the difference between a catalogue and an
                // inventory, so it earns a line of its own rather than being
                // buried in the subtitle with the folder name.
                if (item.tracksStock) {
                    Text(
                        when (val stock = item.stock) {
                            null -> ""
                            0 -> "Épuisé"
                            1 -> "1 en stock"
                            else -> "$stock en stock"
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Plus d'actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceDetailSheet(
    record: InvoiceRecord,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onExportXml: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = record.status.label,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    record.data.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(record.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${record.data.header} du ${record.data.date}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                record.data.total.asDirhams(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Total TTC",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            DetailRow("Articles", "${record.data.lines.size}")
            DetailRow("Total HT", record.data.totalExcludingVat.asDirhams())
            DetailRow(
                if (record.data.effectiveVatPercent == 0.0) "TVA (exonérée)" else "TVA ${record.data.effectiveVatPercent.trimZeros()} %",
                record.data.vatAmount.asDirhams(),
            )
            if (record.data.discountPercent > 0) {
                DetailRow("Remise", "−${record.data.discountPercent} %")
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Partager le PDF")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportXml,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Exporter en XML")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDuplicate,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dupliquer en nouvelle facture")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Supprimer de l'historique", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            fontWeight = FontWeight.SemiBold,
        )
    }
}
