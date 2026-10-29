package com.yassine.facturemaroc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.ui.BuilderScreen
import com.yassine.facturemaroc.ui.FactureMarocTheme
import com.yassine.facturemaroc.ui.HomeScreen
import com.yassine.facturemaroc.ui.ShopDetailsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: InvoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]
        setContent {
            FactureMarocTheme {
                FactureMarocApp(viewModel = viewModel)
            }
        }
    }
}

/** Where the user is. Three places, one back behaviour. */
private sealed interface Destination {
    data object Home : Destination
    data object Builder : Destination
    data object Settings : Destination
}

@Composable
fun FactureMarocApp(viewModel: InvoiceViewModel) {
    var destination by remember { mutableStateOf<Destination>(Destination.Home) }
    val context = LocalContext.current

    BackHandler(enabled = destination != Destination.Home) {
        if (destination == Destination.Builder) viewModel.saveDraftNow()
        destination = Destination.Home
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (destination) {
            Destination.Home -> HomeScreen(
                viewModel = viewModel,
                onNewInvoice = { client ->
                    viewModel.newDraft()
                    if (client != null) viewModel.setClient(client)
                    destination = Destination.Builder
                },
                onNewOrder = { supplier ->
                    // A bon de commande is addressed to a supplier, so opening
                    // it from the suppliers list has to switch the document
                    // type as well — otherwise the supplier would be typed in
                    // as the customer of a facture.
                    viewModel.newDraft()
                    viewModel.setDocumentType(DocumentType.BON_DE_COMMANDE)
                    viewModel.setSupplier(supplier)
                    destination = Destination.Builder
                },
                onOpenRecord = { record ->
                    viewModel.loadRecord(record)
                    destination = Destination.Builder
                },
                onOpenSettings = { destination = Destination.Settings },
            )

            Destination.Builder -> BuilderScreen(
                viewModel = viewModel,
                onBack = { destination = Destination.Home },
                onOpenSettings = { destination = Destination.Settings },
            )

            Destination.Settings -> ShopDetailsScreen(
                context = context,
                viewModel = viewModel,
                onBack = { destination = Destination.Home },
            )
        }
    }
}
