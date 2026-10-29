package com.yassine.facturemaroc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.InvoiceViewModel

/**
 * Chooses the document's paper layout, by looking at it.
 *
 * Six thumbnails rather than six names: "Facture Maroc" and "Classique" mean
 * nothing to a shop until it has seen both, and a template chosen by name is
 * chosen by accident.
 *
 * Each thumbnail is a real PDF render of the template with the current draft
 * data *and the current design* — the accent and the switches change it, so a
 * preview that ignored them would show a document the shop is not about to
 * get. The six re-render whenever any knob moves. A hand-drawn sketch is shown
 * while a render loads or if rendering fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignPickerSheet(
    viewModel: InvoiceViewModel,
    design: InvoiceDesign,
    onSelect: (InvoiceDesign) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templates = InvoiceTemplate.entries
    val density = LocalDensity.current
    val widthPx = (density.density * 72f).toInt()

    /** One live render per template, populated asynchronously as they load. */
    val previews = remember { mutableStateMapOf<InvoiceTemplate, Bitmap?>() }

    templates.forEach { template ->
        LaunchedEffect(template, design) {
            previews[template] = viewModel.previewBitmapFor(design.copy(template = template), widthPx)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("Modèle de document", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "Le modèle s'applique à tous les documents. Le type de document reste choisi sur la facture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            templates.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { template ->
                        TemplateCard(
                            template = template,
                            preview = previews[template],
                            selected = template == design.template,
                            onClick = { onSelect(design.copy(template = template)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ---- colour
            Text("Couleur", style = MaterialTheme.typography.titleSmall)
            Text(
                if (design.template.isRoll) {
                    "Le ticket 80 mm s'imprime en noir : cette couleur ne s'y applique pas."
                } else {
                    "La couleur du modèle. Elle ne teinte jamais le texte de la facture."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InvoiceDesign.ACCENT_PRESETS.forEach { hex ->
                    AccentSwatch(
                        hex = hex,
                        selected = design.accent.equals(hex, ignoreCase = true),
                        enabled = !design.template.isRoll,
                        onClick = { onSelect(design.copy(accent = hex)) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // ---- what prints
            Text(
                "Ce qui s'imprime",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            Text(
                "Tout est imprimé par défaut. Ces réglages valent pour tous les documents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = "Logo du magasin",
                subtitle = if (design.template.isRoll) {
                    "Sans effet sur le ticket 80 mm, qui s'imprime en une couleur."
                } else {
                    "Le logo apparaît en haut du document."
                },
                checked = design.logoEnabled,
                enabled = !design.template.isRoll,
                onCheckedChange = { onSelect(design.copy(logoEnabled = it)) },
            )
            SwitchRow(
                title = "Identifiants légaux",
                subtitle = "ICE, IF, RC, patente et CNSS, pour le magasin et le client.",
                checked = design.showLegalIdentifiers,
                onCheckedChange = { onSelect(design.copy(showLegalIdentifiers = it)) },
            )
            SwitchRow(
                title = "Montant en lettres",
                subtitle = "Le total écrit en toutes lettres, sous les totaux.",
                checked = design.showAmountWords,
                onCheckedChange = { onSelect(design.copy(showAmountWords = it)) },
            )
            SwitchRow(
                title = "Mode de règlement",
                subtitle = "Espèces, chèque, virement — quand la facture en porte un.",
                checked = design.showPaymentMethod,
                onCheckedChange = { onSelect(design.copy(showPaymentMethod = it)) },
            )
            SwitchRow(
                title = "Pied de page",
                subtitle = "Un mot de remerciement en bas du document.",
                checked = design.showFooter,
                onCheckedChange = { onSelect(design.copy(showFooter = it)) },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccentSwatch(
    hex: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000L)
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(30.dp)
            .background(if (enabled) color else color.copy(alpha = 0.35f), CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else outline,
                shape = CircleShape,
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Couleur choisie",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun TemplateCard(
    template: InvoiceTemplate,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                contentAlignment = Alignment.Center,
            ) {
                val pageModifier = if (template.isRoll) {
                    Modifier
                        .width(58.dp)
                        .aspectRatio(0.52f)
                } else {
                    Modifier
                        .width(72.dp)
                        .aspectRatio(0.72f)
                }
                Surface(
                    modifier = pageModifier,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Aperçu du modèle ${template.label}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TemplateSketch(template)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Modèle choisi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                templateDescription(template),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
        }
    }
}

/**
 * A page drawn in blocks, shown while the real render loads.
 *
 * It reads as a page rather than as an icon: header, table, totals. The point
 * is that a shop can tell the six apart at a glance, not that it is accurate —
 * the thumbnail replaces it as soon as the PDF render is ready.
 */
@Composable
private fun TemplateSketch(template: InvoiceTemplate) {
    val accent = MaterialTheme.colorScheme.primary
    val softAccent = accent.copy(alpha = 0.16f)
    val ink = Color(0xFF4A5568)
    val hair = Color(0xFFE2E8F0)

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val u = w / 100f

        fun block(x: Float, y: Float, width: Float, height: Float, color: Color) {
            drawRect(color, Offset(x * u, y * h), Size(width * u, height * h))
        }

        // Bandeau's colour reaches the top edge, so it goes down before the
        // header marks and they switch to white to stay legible on it.
        if (template == InvoiceTemplate.BANDEAU) block(0f, 0f, 100f, 18f, accent)
        val onBand = template == InvoiceTemplate.BANDEAU
        val headInk = if (onBand) Color.White else ink
        val headSoft = if (onBand) Color.White.copy(alpha = 0.55f) else hair
        val mark = if (onBand) Color.White.copy(alpha = 0.9f) else accent

        // Header: logo mark and the shop's two lines.
        block(5f, 4f, 15f, 7f, mark)
        block(24f, 5f, 44f, 3.5f, headInk)
        block(24f, 11f, 30f, 2.6f, headSoft)
        block(84f, 5f, 11f, 3f, headSoft)

        when (template) {
            InvoiceTemplate.MAROC -> {
                block(0f, 20f, 100f, 11f, softAccent)
                block(28f, 23.5f, 44f, 4.5f, accent)
            }

            InvoiceTemplate.MODERNE -> {
                block(5f, 19f, 90f, 3f, accent)
            }

            InvoiceTemplate.EPURE -> {
                // A short accent tick, then nothing but air and a hairline.
                block(5f, 20f, 13f, 1.4f, accent)
                block(5f, 25f, 34f, 4.5f, ink)
                block(5f, 33f, 90f, 0.7f, hair)
            }

            InvoiceTemplate.BANDEAU -> {
                block(5f, 22f, 26f, 2.6f, accent)
                block(5f, 27f, 20f, 2f, hair)
            }

            InvoiceTemplate.TICKET -> {
                block(5f, 21f, 90f, 1.2f, ink)
                block(30f, 25f, 40f, 3f, ink)
            }

            InvoiceTemplate.CLASSIQUE -> {
                block(5f, 20f, 90f, 1.4f, ink)
                block(5f, 24f, 38f, 3f, ink)
            }
        }

        // The table's head and three rows.
        block(5f, 38f, 90f, 5.5f, hair)
        repeat(3) { row ->
            val y = 46f + row * 10f
            block(5f, y, 46f, 3f, ink)
            block(70f, y, 12f, 3f, hair)
            block(86f, y, 9f, 3f, ink)
        }

        // Totals, right aligned, with the TTC in the accent colour.
        block(58f, 76f, 37f, 13f, hair)
        block(60f, 78f, 14f, 2.6f, ink)
        block(78f, 78f, 15f, 2.6f, ink)
        block(60f, 83.5f, 12f, 2.6f, ink)
        if (template == InvoiceTemplate.BANDEAU) {
            // The foot band, the only other place the colour lands.
            block(58f, 84f, 37f, 5.5f, accent)
            block(60f, 85.5f, 10f, 2.2f, Color.White)
            block(80f, 85.5f, 13f, 2.4f, Color.White)
        } else {
            block(78f, 83.5f, 15f, 2.6f, accent)
        }
    }
}

private fun templateDescription(template: InvoiceTemplate): String = when (template) {
    InvoiceTemplate.CLASSIQUE -> "Sobre et universel, sans couleur."
    InvoiceTemplate.MODERNE -> "Titre large, filet de couleur, lignes aérées."
    InvoiceTemplate.MAROC -> "Cadre TVA et mentions légales."
    InvoiceTemplate.EPURE -> "Typographique, beaucoup de blanc."
    InvoiceTemplate.BANDEAU -> "Bandeaux de couleur en haut et en bas."
    InvoiceTemplate.TICKET -> "Reçu étroit pour imprimante 80 mm."
}
