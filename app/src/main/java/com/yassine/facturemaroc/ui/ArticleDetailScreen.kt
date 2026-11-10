package com.yassine.facturemaroc.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.Money
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.data.SavedItemStore
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.StockEntry
import com.yassine.facturemaroc.data.StockReason

/**
 * One article, with what it costs, what it earns and where its stock went.
 *
 * Tapping a row used to open the editor, which answers "what did I type?" but
 * not the question a shop has while looking at the catalogue: "what does this
 * earn me, and how many are left?". The page shows the figures first and puts
 * the form behind « Modifier », the same shape as a client's page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    item: SavedItem,
    shop: ShopSettings,
    /** The weighted cost of one unit, as the valuation method computes it. */
    unitCost: Double,
    /** Newest first; only what fits is drawn, the full history is one tap away. */
    entries: List<StockEntry>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onMove: (() -> Unit)? = null,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val service = item.stock == null
    val empty = !service && item.outOfStock

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (onMove != null) {
                                DropdownMenuItem(
                                    text = { Text("Déplacer dans un dossier") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Folder, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onMove()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Modifier")
                    }
                    Button(
                        onClick = onHistory,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Mouvements")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "figures") {
                FigureCard(item = item, shop = shop, unitCost = unitCost, empty = empty)
            }

            item(key = "identity") {
                DetailCard(
                    title = if (service) "Prestation" else "Article",
                    rows = buildList {
                        add("Type" to if (service) "Prestation, non comptée" else "Produit, suivi en stock")
                        if (item.folder.isNotBlank()) {
                            add("Dossier" to SavedItemStore.display(item.folder))
                        }
                        if (item.unit.isNotBlank()) add("Unité" to item.unit)
                        if (item.barcode.isNotBlank()) add("Code-barres" to item.barcode)
                        item.reorderThreshold?.let { add("Seuil de réapprovisionnement" to "$it") }
                    },
                )
            }

            if (item.priceTiers.isNotEmpty()) {
                item(key = "tiers") {
                    DetailCard(
                        title = "Prix par quantité",
                        rows = item.priceTiers.map {
                            "À partir de ${it.fromQuantity}" to Money.dirhams(it.unitPrice)
                        },
                    )
                }
            }

            if (item.attributes.isNotEmpty()) {
                item(key = "attributes") {
                    DetailCard(
                        title = "Caractéristiques",
                        rows = item.attributes.map { it.key to it.value },
                    )
                }
            }

            if (!service) {
                item(key = "movements-heading") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Derniers mouvements",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        if (entries.isNotEmpty()) {
                            Text(
                                "${entries.size} au total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "movements-empty") {
                        Hint("Aucun mouvement enregistré pour cet article.")
                    }
                } else {
                    items(entries.take(6), key = { "mv-${it.at}-${it.delta}-${it.reason.id}" }) { entry ->
                        MovementRow(entry)
                    }
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** The one card a shop came for: price, what it leaves, and what is on the shelf. */
@Composable
private fun FigureCard(
    item: SavedItem,
    shop: ShopSettings,
    unitCost: Double,
    empty: Boolean,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (shop.pricesIncludeVat) "Prix TTC" else "Prix HT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
            Text(
                Money.dirhams(item.price),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // The margin is computed on the HT sale price, because the cost
                // is HT: comparing a TTC price to an HT cost invents VAT as
                // profit, and a shop would believe it earned more than it did.
                val saleHt = if (
                    shop.pricesIncludeVat && !shop.vatNotApplicable && shop.vatPercent > 0.0
                ) {
                    item.price / (1.0 + shop.vatPercent / 100.0)
                } else {
                    item.price
                }
                if (unitCost > 0.0) {
                    val margin = saleHt - unitCost
                    val percent = if (saleHt > 0.0) {
                        Math.round(margin / saleHt * 100.0).toInt()
                    } else {
                        0
                    }
                    HeroFigure(
                        label = "Marge",
                        value = "${Money.dirhams(margin)} · $percent %",
                        warn = margin < 0.0,
                    )
                }
                HeroFigure(
                    label = "Coût d'achat HT",
                    value = if (unitCost > 0.0) Money.dirhams(unitCost) else "Non renseigné",
                )
                if (item.stock != null) {
                    HeroFigure(
                        label = if (empty) "Stock épuisé" else "En stock",
                        value = "${item.stock}",
                        warn = empty,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, warn: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = if (warn) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailCard(title: String, rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementRow(entry: StockEntry) {
    val icon = when {
        entry.delta > 0 -> Icons.Filled.Inventory2
        entry.reason == StockReason.INVOICE -> Icons.Filled.Handyman
        else -> Icons.Filled.Inventory2
    }
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.reason.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (entry.reference.isNotBlank() || entry.note.isNotBlank()) {
                    Text(
                        listOf(entry.reference, entry.note)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (entry.delta > 0) "+${entry.delta}" else "${entry.delta}",
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (entry.delta < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
