package com.yassine.facturemaroc.invoice

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders the payment payload into a square bitmap for the PDF.
 *
 * ZXing's core is pure Java and has no Android cost beyond its size; the
 * bitmap is what [android.graphics.Canvas] can draw. The text itself comes
 * from [com.yassine.facturemaroc.data.PaymentQrText], so the wording can be
 * tested on the JVM without a bitmap.
 */
object PaymentQr {

    private const val QUIET_ZONE = 1

    /** A black-on-white QR, or null when the payload is blank or unencodable. */
    fun bitmap(payload: String, sizePx: Int = 240): Bitmap? {
        if (payload.isBlank()) return null
        return try {
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to QUIET_ZONE),
            )
            val bitmap = Bitmap.createBitmap(
                matrix.width,
                matrix.height,
                Bitmap.Config.ARGB_8888,
            )
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            // A QR that will not encode must not stop the invoice: the bank
            // details are printed as text beside it either way.
            null
        }
    }
}
