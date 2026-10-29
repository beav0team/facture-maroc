package com.yassine.facturemaroc.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode
import com.google.mlkit.vision.common.InputImage
import com.yassine.facturemaroc.data.Barcode as StoredBarcode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads a barcode with the camera and hands back the digits.
 *
 * The camera is the only permission this app asks for, and it is asked for at
 * the moment the shop taps the scan button, not at launch: an app that demands
 * its permissions before showing anything is an app that is uninstalled at
 * that screen. Refusing costs nothing — the code can still be typed.
 *
 * The decoding happens on the phone (ML Kit's bundled model), so the scanner
 * works with no network, no Google account and no Play services, and no frame
 * ever leaves the device.
 *
 * A scan is accepted once and the dialog closes: a viewfinder that keeps firing
 * while the shop moves the box would set the field three times and leave the
 * last, blurriest read.
 */
@Composable
fun BarcodeScannerDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var deniedOnce by remember { mutableStateOf(false) }
    // Asked automatically the first time, because the shop tapped the button
    // that says "scan" and a second tap to confirm is a tap too many.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        if (!allowed) deniedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Autoriser l'appareil photo ?") },
            text = {
                Text(
                    if (deniedOnce) {
                        // Android stops showing the system dialog after a second
                        // refusal, so offering "Autoriser" again would be a
                        // button that does nothing. The way back is Settings.
                        "L'appareil photo est refusé. Le code-barres se tape à la main, " +
                            "ou autorisez la caméra dans les réglages de l'application."
                    } else {
                        "La caméra sert uniquement à lire le code-barres de l'article. " +
                            "La lecture se fait sur le téléphone : aucune photo ne quitte l'appareil."
                    },
                )
            },
            confirmButton = {
                if (deniedOnce) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                            onDismiss()
                        },
                    ) {
                        Text("Ouvrir les réglages")
                    }
                } else {
                    TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Autoriser")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Saisir à la main") }
            },
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            BarcodeCamera(onScanned = onScanned, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun BarcodeCamera(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failure by remember { mutableStateOf<String?>(null) }
    val handled = remember { AtomicBoolean(false) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                MlKitBarcode.FORMAT_EAN_13,
                MlKitBarcode.FORMAT_EAN_8,
                MlKitBarcode.FORMAT_UPC_A,
                MlKitBarcode.FORMAT_UPC_E,
                MlKitBarcode.FORMAT_CODE_128,
                MlKitBarcode.FORMAT_CODE_39,
                MlKitBarcode.FORMAT_ITF,
                MlKitBarcode.FORMAT_QR_CODE,
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var bound by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            bound?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (failure == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { view ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener(
                        {
                            try {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(view.surfaceProvider)
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(
                                            executor,
                                            BarcodeAnalyzer(scanner, handled) { value ->
                                                // Back to the main thread: the value
                                                // is about to become Compose state.
                                                ContextCompat.getMainExecutor(context).execute {
                                                    onScanned(value)
                                                }
                                            },
                                        )
                                    }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                                bound = provider
                            } catch (t: Throwable) {
                                failure = "Aucune caméra utilisable sur cet appareil. " +
                                    "Saisissez le code à la main."
                            }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                },
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    failure.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                // The X in the corner closes too, but a dead end needs a
                // sentence saying what to do next, not a symbol to interpret.
                TextButton(onClick = onDismiss) {
                    Text("Saisir à la main", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Frame and hint belong to a working viewfinder. With no camera they
        // would sit on top of the failure sentence and box it in, which reads
        // as a second, competing instruction.
        if (failure == null) {
            // A square frame to aim with. The scanner reads the whole image, so
            // this is guidance for the hand, not a crop.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
            )

            Text(
                "Placez le code-barres dans le cadre",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
        }
    }
}

/**
 * Feeds frames to ML Kit one at a time and reports the first readable code.
 *
 * [handled] is shared with the dialog so a second frame cannot overwrite the
 * value that already closed it, and every frame is closed whether it decoded or
 * not — a proxy left open stalls the camera after five frames, which looks
 * exactly like a scanner that stopped working.
 */
@OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val handled: AtomicBoolean,
    private val onValue: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    /**
     * True while ML Kit is still working on a frame.
     *
     * [analyze] returns before the decode finishes, so without this the camera
     * hands over frame after frame and the decoder ends up working through a
     * backlog of views the shop has already moved past. One in flight at a time
     * means the code being aimed at is the code being read.
     */
    private val inFlight = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || handled.get() || !inFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (value != null && handled.compareAndSet(false, true)) {
                    onValue(StoredBarcode.clean(value))
                }
            }
            .addOnCompleteListener {
                inFlight.set(false)
                imageProxy.close()
            }
    }
}

/** Whether this device can scan at all, so the button can be hidden rather than fail. */
fun hasCamera(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
