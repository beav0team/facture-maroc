package com.yassine.facturemaroc

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.yassine.facturemaroc.data.Barcode as StoredBarcode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scanner, checked against a real barcode image on the device.
 *
 * A camera cannot be pointed at a box from a test, so the decoded image is
 * shipped as an asset instead: the same ML Kit client the dialog runs, the same
 * formats, the same post-processing. What this cannot cover is the CameraX
 * plumbing — the viewfinder, the analyzer thread, the frame closing — and that
 * is what has to be tried by hand.
 *
 * The asset is a standard EAN-13 carrying `6111234500014`, generated with the
 * `python-barcode` encoder rather than typed in from memory. The check digit is
 * the encoder's, not mine: the first version of this test expected a digit I
 * had invented for the demo catalogue, and the scanner was right.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeDecodeTest {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE,
            )
            .build(),
    )

    private fun decode(asset: String): String? {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        val latch = CountDownLatch(1)
        var found: String? = null
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes ->
                found = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        assertTrue("the scanner did not answer within 20 s", latch.await(20, TimeUnit.SECONDS))
        return found
    }

    // Method names carry no spaces on purpose: an instrumented test is compiled
    // into a dex, and DEX before version 040 refuses spaces in a member name.
    @Test
    fun anEan13PrintedOnABoxIsReadBackDigitForDigit() {
        assertEquals("6111234500014", decode("ean13.png"))
    }

    @Test
    fun whatTheScannerReadsIsWhatGetsStored() {
        // The dialog passes every value through Barcode.clean before it reaches
        // the field, so the stored code is the one searched for later.
        assertEquals("6111234500014", StoredBarcode.clean(decode("ean13.png").orEmpty()))
    }
}
