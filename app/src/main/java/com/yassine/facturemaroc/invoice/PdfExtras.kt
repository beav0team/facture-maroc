package com.yassine.facturemaroc.invoice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.TextPaint
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.PaymentQrText
import com.yassine.facturemaroc.data.ShopSettings

/**
 * The free-text notes and the signature, drawn the same way on every layout.
 *
 * These are the only parts of a document that the shop writes itself, so they
 * get one implementation rather than six: a note that wraps differently on
 * Classique and Maroc would be a second place to fix the same bug, and a
 * signature that is cropped on one template is a stamp the customer cannot
 * read.
 *
 * Both are optional. A document with neither draws exactly what it drew before
 * these existed, which is why every function here returns the y it was given.
 */
object PdfExtras {

    /** The signature box, in points, on the A4 layouts. */
    const val SIGNATURE_W = 150f
    const val SIGNATURE_H = 46f

    private const val CAPTION_H = 11f

    /** How much paper the notes need, including the gap after them. */
    fun notesHeight(
        data: InvoiceData,
        label: TextPaint,
        body: TextPaint,
        width: Int,
        maxLines: Int = 3,
    ): Float {
        if (!data.hasNotes || width <= 0) return 0f
        var h = 0f
        val customer = data.customerNote.trim()
        if (customer.isNotEmpty()) {
            h += PdfText.height("NOTE", label, width, 1) + 1f
            h += PdfText.height(customer, body, width, maxLines) + 8f
        }
        val terms = data.termsNote.trim()
        if (terms.isNotEmpty()) {
            h += PdfText.height("CONDITIONS", label, width, 1) + 1f
            h += PdfText.height(terms, body, width, maxLines) + 8f
        }
        val clause = data.latePenaltyClause.trim()
        if (data.printsLateClause && clause.isNotEmpty()) {
            h += PdfText.height("RETARD DE PAIEMENT", label, width, 1) + 1f
            h += PdfText.height(clause, body, width, maxLines) + 8f
        }
        return h
    }

    /** Draws the notes at [x], [y] and returns the y after them. */
    fun drawNotes(
        canvas: Canvas,
        data: InvoiceData,
        x: Float,
        y: Float,
        width: Int,
        label: TextPaint,
        body: TextPaint,
        maxLines: Int = 3,
    ): Float {
        if (!data.hasNotes || width <= 0) return y
        var cursor = y

        val customer = data.customerNote.trim()
        if (customer.isNotEmpty()) {
            PdfText.draw(canvas, "NOTE", x, cursor, label, width, maxLines = 1)
            cursor += PdfText.height("NOTE", label, width, 1) + 1f
            PdfText.draw(canvas, customer, x, cursor, body, width, maxLines = maxLines)
            cursor += PdfText.height(customer, body, width, maxLines) + 8f
        }

        val terms = data.termsNote.trim()
        if (terms.isNotEmpty()) {
            PdfText.draw(canvas, "CONDITIONS", x, cursor, label, width, maxLines = 1)
            cursor += PdfText.height("CONDITIONS", label, width, 1) + 1f
            PdfText.draw(canvas, terms, x, cursor, body, width, maxLines = maxLines)
            cursor += PdfText.height(terms, body, width, maxLines) + 8f
        }

        // A model clause the shop had checked: printed only when the document
        // is not paid at the counter, and only when the shop filled it in.
        val clause = data.latePenaltyClause.trim()
        if (data.printsLateClause && clause.isNotEmpty()) {
            PdfText.draw(canvas, "RETARD DE PAIEMENT", x, cursor, label, width, maxLines = 1)
            cursor += PdfText.height("RETARD DE PAIEMENT", label, width, 1) + 1f
            PdfText.draw(canvas, clause, x, cursor, body, width, maxLines = maxLines)
            cursor += PdfText.height(clause, body, width, maxLines) + 8f
        }

        return cursor
    }

    /** How much paper the signature needs, caption included; 0 when absent. */
    fun signatureHeight(data: InvoiceData): Float =
        if (data.hasSignature) SIGNATURE_H + CAPTION_H + 8f else 0f

    /**
     * Draws the signature image fitted inside a [width] × [height] box.
     *
     * The image is never stretched and never cropped: a photographed stamp
     * arrives at whatever aspect ratio the phone camera produced, and a
     * squashed signature on a legal document reads as a forged one.
     * [drawCaption] is off where the template already prints its own label.
     */
    fun drawSignature(
        canvas: Canvas,
        data: InvoiceData,
        x: Float,
        y: Float,
        width: Float = SIGNATURE_W,
        height: Float = SIGNATURE_H,
        caption: TextPaint? = null,
        drawCaption: Boolean = true,
        captionText: String = "Signature",
    ): Float {
        val image = decode(data) ?: return y
        val scale = kotlin.math.min(width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val target = RectF(
            x + (width - w) / 2f,
            y + (height - h) / 2f,
            x + (width + w) / 2f,
            y + (height + h) / 2f,
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(image, null, target, paint)
        if (drawCaption && caption != null) {
            PdfText.draw(
                canvas, captionText, x, y + height + 1f, caption, width.toInt(),
                Layout.Alignment.ALIGN_CENTER, maxLines = 1,
            )
        }
        return y + height + if (drawCaption) CAPTION_H + 4f else 0f
    }

    /** The QR square, in points, when the design asks for one. */
    private const val QR_SIZE = 58f

    /** Whether this document prints a payment block at all. */
    fun paymentBlockShown(data: InvoiceData, shop: ShopSettings?, design: InvoiceDesign?): Boolean {
        val bank = shop != null && shop.hasBankDetails
        val qr = design != null && design.showPaymentQr && shop != null &&
            PaymentQrText.of(data, shop) != null
        return bank || qr ||
            data.paymentState != com.yassine.facturemaroc.data.PaymentState.UNPAID ||
            data.dueDatePrinted != null ||
            data.deposit > 0.0
    }

    /** The bank lines, in the order a customer reads them; empty when none. */
    private fun bankLines(shop: ShopSettings): List<String> = buildList {
        if (shop.bankName.isNotBlank()) add("Banque : ${shop.bankName.trim()}")
        if (shop.bankRib.isNotBlank()) add("RIB : ${shop.bankRib.trim()}")
        if (shop.bankHolder.isNotBlank()) add("Titulaire : ${shop.bankHolder.trim()}")
    }

    /**
     * Height of the payment block; 0 when nothing was received and nothing else
     * makes it print. The caller reserves this before the page is broken.
     */
    fun paymentHeight(
        data: InvoiceData,
        shop: ShopSettings? = null,
        design: InvoiceDesign? = null,
        label: TextPaint,
        body: TextPaint,
    ): Float {
        if (!paymentBlockShown(data, shop, design)) return 0f
        val due = data.dueDatePrinted != null
        val state = data.paymentState
        val deposit = data.deposit > 0.0
        val note = data.payment?.note?.trim().orEmpty()
        val paidLines = when (state) {
            com.yassine.facturemaroc.data.PaymentState.UNPAID -> if (deposit) 1 else 0
            com.yassine.facturemaroc.data.PaymentState.PAID -> 1
            else -> 2
        }
        // A settled document still shows the deposit line and the zero balance.
        val depositLines = if (deposit) 1 else 0
        val lines = paidLines + depositLines + if (due) 1 else 0
        var h = lines * (body.textSize + 4f) +
            if (note.isNotEmpty()) label.textSize + 4f else 0f
        if (shop != null) {
            h += bankLines(shop).size * (body.textSize + 4f)
            val qr = design != null && design.showPaymentQr &&
                PaymentQrText.of(data, shop) != null
            if (qr) h += QR_SIZE + 10f
        }
        return h
    }

    /**
     * Prints what the shop received.
     *
     * Only a document that carries a payment prints this, so an unpaid invoice
     * looks exactly as it did before the feature existed. A settled document
     * says so in one line; a partial one prints both figures, because "Payé
     * 500" alone leaves the customer guessing what is left.
     */
    fun drawPayment(
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings? = null,
        design: InvoiceDesign? = null,
        x: Float,
        y: Float,
        width: Float,
        label: TextPaint,
        body: TextPaint,
        bold: TextPaint,
    ): Float {
        if (!paymentBlockShown(data, shop, design)) return y
        var cursor = y
        val date = data.payment?.paidAt?.trim().orEmpty()
        // What actually came in, when the shop recorded a different method
        // than the one the document was written for.
        val how = data.payment?.method?.french?.takeIf { it.isNotBlank() }

        // Printed whether or not money came in: an unpaid document that names
        // its deadline is the one a customer is most likely to check.
        data.dueDatePrinted?.let { due ->
            PdfText.draw(canvas, "Échéance : $due", x, cursor, body, width.toInt(), maxLines = 1)
            cursor += body.textSize + 4f
        }
        // A deposit is money already received on another document, so it is
        // stated before what was received here — and it reduces the balance
        // without touching the invoice's VAT, which was taxed on the deposit.
        if (data.deposit > 0.0) {
            val ref = data.depositReference.trim()
            val label = if (ref.isEmpty()) "Acompte déjà versé" else "Acompte déjà versé ($ref)"
            PdfText.draw(
                canvas, "$label : ${PdfText.money(data.deposit)}", x, cursor, body,
                width.toInt(), maxLines = 1,
            )
            cursor += body.textSize + 4f
        }
        if (data.paymentState == com.yassine.facturemaroc.data.PaymentState.UNPAID) {
            if (data.deposit > 0.0) {
                PdfText.draw(
                    canvas, "Reste à payer : ${PdfText.money(data.balanceDue)}",
                    x, cursor, bold, width.toInt(), maxLines = 1,
                )
                cursor += bold.textSize + 4f
            }
        }

        if (data.paymentState == com.yassine.facturemaroc.data.PaymentState.PAID) {
            val isInvoiceType =
                data.documentType == com.yassine.facturemaroc.data.DocumentType.FACTURE
            val settled = when {
                data.deposit > 0.0 && data.paidAmount <= 0.0 ->
                    if (isInvoiceType) "Facture soldée par acompte" else "Document soldé par acompte"
                isInvoiceType -> "Facture acquittée"
                else -> "Document acquitté"
            }
            val line = listOfNotNull(
                settled,
                date.takeIf { it.isNotEmpty() }?.let { "le $it" },
                how?.let { "· $it" },
            ).joinToString(" ")

            PdfText.draw(canvas, line, x, cursor, bold, width.toInt(), maxLines = 1)
            cursor += bold.textSize + 4f
        } else if (data.paymentState == com.yassine.facturemaroc.data.PaymentState.PARTIAL) {
            val head = if (date.isEmpty()) "Payé" else "Payé le $date"
            val paid = buildString {
                append(head)
                append(" : ")
                append(PdfText.money(data.paidAmount))
                if (how != null) append(" ($how)")
            }
            PdfText.draw(canvas, paid, x, cursor, body, width.toInt(), maxLines = 1)
            cursor += body.textSize + 4f
            PdfText.draw(
                canvas, "Reste à payer : ${PdfText.money(data.balanceDue)}",
                x, cursor, bold, width.toInt(), maxLines = 1,
            )
            cursor += bold.textSize + 4f
        }

        val note = data.payment?.note?.trim().orEmpty()
        if (note.isNotEmpty()) {
            PdfText.draw(canvas, "Réf. $note", x, cursor, label, width.toInt(), maxLines = 1)
            cursor += label.textSize + 4f
        }

        // Bank details whenever the shop filled them: an unpaid invoice with no
        // deadline is still the document a customer needs the RIB for.
        if (shop != null) {
            bankLines(shop).forEach { line ->
                PdfText.draw(canvas, line, x, cursor, body, width.toInt(), maxLines = 2)
                cursor += body.textSize + 4f
            }
            val payload = if (design != null && design.showPaymentQr) {
                PaymentQrText.of(data, shop)
            } else {
                null
            }
            if (payload != null) {
                PaymentQr.bitmap(payload)?.let { image ->
                    // Left-aligned with the bank lines: a code centred in a
                    // wide block floats away from the account it belongs to.
                    val top = cursor + 4f
                    canvas.drawBitmap(
                        image,
                        null,
                        RectF(x, top, x + QR_SIZE, top + QR_SIZE),
                        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
                    )
                    cursor = top + QR_SIZE + 6f
                }
            }
        }
        return cursor
    }

    private fun decode(data: InvoiceData): Bitmap? {
        val bytes = data.signature?.bytes ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
}
