package com.yassine.facturemaroc.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.ShopSettingsStore

@Composable
fun ShopDetailsScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val store = ShopSettingsStore(context)
    val settings by store.settings.collectAsState()

    var name by remember { mutableStateOf(settings.name) }
    var address by remember { mutableStateOf(settings.address) }
    var ice by remember { mutableStateOf(settings.ice) }
    var ifNumber by remember { mutableStateOf(settings.ifNumber) }
    var rc by remember { mutableStateOf(settings.rc) }
    var patente by remember { mutableStateOf(settings.patente) }
    var cnss by remember { mutableStateOf(settings.cnss) }
    var vatPercent by remember { mutableStateOf(settings.vatPercent) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Vertical.Center,
        ) {
            Text(
                "Shop details",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Back")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Set your company info once; it prints on every invoice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Company name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Company address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = ice,
                onValueChange = { ice = it },
                label = { Text("ICE") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = ifNumber,
                onValueChange = { ifNumber = it },
                label = { Text("IF") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = rc,
                onValueChange = { rc = it },
                label = { Text("RC") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = patente,
                onValueChange = { patente = it },
                label = { Text("Patente") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = cnss,
                onValueChange = { cnss = it },
                label = { Text("CNSS") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = vatPercent.toString(),
            onValueChange = { vatPercent = it.replace(',', '.').toDoubleOrNull() ?: 20.0 },
            label = { Text("TVA default %") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.Center,
        ) {
            Text(
                "Logo",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { }) {
                Text("Change logo")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    store.write(context, ShopSettings(
                        name = name,
                        address = address,
                        ice = ice,
                        ifNumber = ifNumber,
                        rc = rc,
                        patente = patente,
                        cnss = cnss,
                        vatPercent = vatPercent,
                    ))
                onBack()
            },
            shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Save shop details")
            }
        }
    }
}
