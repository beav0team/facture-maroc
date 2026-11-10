package com.yassine.facturemaroc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The order control both lists share.
 *
 * The catalogue and the directory each grew their own version, and they had
 * already drifted: one showed the current order, the other did not. One
 * component means a list can no longer be sorted by a rule nobody can see.
 *
 * A dropdown rather than a row of chips: the chips underneath are already a row,
 * and a second row of filters above the first article pushes content off a 360
 * dp screen.
 */
@Composable
fun SortRow(
    /** The order currently applied, written on the control. */
    label: String,
    /** Every order the list accepts, in the order they are offered. */
    options: List<String>,
    /** The index in [options] that is active. */
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { open = true }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Trier : $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    leadingIcon = {
                        if (index == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/**
 * A row of single-choice chips: « Tous · Doit de l'argent · En retard ».
 *
 * [counts] prints the number behind each chip when there is one, so the shop
 * can see that four clients are late without opening the filter.
 */
@Composable
fun ChoiceChipsRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int>? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = {
                    val count = counts?.getOrNull(index) ?: 0
                    Text(if (count > 0) "$option ($count)" else option)
                },
                shape = RoundedCornerShape(50),
            )
        }
    }
}
