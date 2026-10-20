package com.yassine.facturemaroc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.yassine.facturemaroc.data.InvoiceViewModel
import com.yassine.facturemaroc.ui.InvoiceScreen
import com.yassine.facturemaroc.ui.ShopDetailsScreen
import com.yassine.facturemaroc.ui.SavedItemListScreen
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: InvoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]
        setContent {
            FactureMarocApp(viewModel = viewModel, context = this)
        }
    }
}

@Composable
fun FactureMarocApp(viewModel: InvoiceViewModel, context: android.content.Context) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Invoice) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (currentScreen) {
            Screen.Invoice -> InvoiceScreen(
                context = context,
                viewModel = viewModel,
                onBack = { },
                onOpenShopSettings = { currentScreen = Screen.ShopDetails },
                onOpenClients = { },
            )
            Screen.ShopDetails -> ShopDetailsScreen(
                context = context,
                onBack = { currentScreen = Screen.Invoice },
            )
            Screen.ItemList -> SavedItemListScreen(
                context = context,
                onBack = { currentScreen = Screen.Invoice },
            )
        }
    }
}

enum class Screen {
    Invoice,
    ShopDetails,
    ItemList,
}
