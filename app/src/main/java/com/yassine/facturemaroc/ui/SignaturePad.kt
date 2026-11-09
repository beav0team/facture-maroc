package com.yassine.facturemaroc.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/** The same cap the invoice signature is stored under; a photo is resized first. */
private const val MAX_SIGNATURE_BYTES = 2 * 1024 * 1024

/**
 * Draws a signature by hand, or takes a photo of one.
 *
 * A drawn signature is captured as strokes rather than as a screenshot of the
 * box, so it is cropped to the ink and printed at print resolution instead of
 * at the phone's screen density. A picked image is downscaled and re-encoded
 * first: a 12 MP photo of a stamp is 4 MB, and an invoice does not need one.
 */
@Composable
fun SignatureDialog(
    initial: ByteArray?,
    onSave: (ByteArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = readSignatureImage(context, uri)
            when {
                bytes == null -> message = "Cette image n'a pas pu être lue, ou dépasse 2 Mo."
                bytes.size > MAX_SIGNATURE_BYTES -> message = "Image trop lourde : 2 Mo maximum."
                else -> onSave(bytes)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initial != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val bitmap = remember(initial) {
                            BitmapFactory.decodeByteArray(initial, 0, initial.size)?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Signature enregistrée",
                                modifier = Modifier
                                    .size(width = 84.dp, height = 42.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(4.dp),
                                    ),
                            )
                        }
                        Text(
                            "Signature enregistrée. Dessinez pour la remplacer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "Dessinez dans le cadre, ou choisissez une image.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    var points = listOf(down.position)
                                    strokes = strokes + listOf(points)
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            points = points + change.position
                                            strokes = strokes.dropLast(1) + listOf(points)
                                            change.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                    ) {
                        strokes.forEach { stroke ->
                            if (stroke.size == 1) {
                                drawCircle(
                                    color = Color.Black,
                                    radius = 1.6f,
                                    center = stroke.first(),
                                )
                            } else {
                                val path = Path().apply {
                                    moveTo(stroke.first().x, stroke.first().y)
                                    stroke.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(
                                        width = 3.2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }
                    }
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Choisir une image")
                    }
                    if (strokes.isNotEmpty()) {
                        TextButton(onClick = { strokes = emptyList() }) {
                            Text("Effacer")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bytes = renderSignature(strokes, canvasSize)
                    if (bytes == null) {
                        message = "Dessinez une signature ou choisissez une image."
                    } else {
                        onSave(bytes)
                    }
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Supprimer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/**
 * Renders the strokes to a cropped PNG.
 *
 * Only the ink is kept: the box is much wider than most signatures, and a
 * mostly-empty image would be scaled down by the PDF until the signature was
 * a few points tall.
 */
internal fun renderSignature(strokes: List<List<Offset>>, canvasSize: IntSize): ByteArray? {
    val points = strokes.flatten()
    if (points.size < 2 || canvasSize.width <= 0 || canvasSize.height <= 0) return null

    val pad = 10f
    val left = (points.minOf { it.x } - pad).coerceAtLeast(0f)
    val top = (points.minOf { it.y } - pad).coerceAtLeast(0f)
    val right = (points.maxOf { it.x } + pad).coerceAtMost(canvasSize.width.toFloat())
    val bottom = (points.maxOf { it.y } + pad).coerceAtMost(canvasSize.height.toFloat())
    val width = right - left
    val height = bottom - top
    if (width < 4f || height < 4f) return null

    val scale = 3f
    val bitmap = Bitmap.createBitmap(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { stroke ->
        if (stroke.size == 1) {
            canvas.drawCircle(stroke.first().x, stroke.first().y, 1.2f, paint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(stroke.first().x, stroke.first().y)
            stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
    }

    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }.takeIf { it.isNotEmpty() }
}

/**
 * Reads a picked image, downscaled to a printable size and re-encoded.
 *
 * Returns null rather than throwing: a picked file that has been deleted
 * between the picker closing and the read is a message, not a crash.
 */
private fun readSignatureImage(context: android.content.Context, uri: Uri): ByteArray? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1200 && bounds.outHeight / (sample * 2) >= 900) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null

    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}.getOrNull()
