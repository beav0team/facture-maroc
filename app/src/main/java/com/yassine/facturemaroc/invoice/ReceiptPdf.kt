package com.yassine.facturemaroc.invoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.yassine.facturemaroc.data.AmountWords
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.PaymentState
import com.yassine.facturemaroc.data.ShopSettings
import java.io.File

/**
 * The paper a customer gets when they hand over money.
 *
 * A deposit is the case this exists for: the invoice says "reste à payer", and
 * the customer wants something that says what they already paid. It is not a
 * second invoice — it carries no lines and no VAT of its own, only the sum
 * received, how it came, and what is left on the document it settles. Printing
 * it on A5 rather than A4 keeps it a receipt instead of a bill.
 *
 * The layout is deliberately its own rather than a template: the six invoice
 * templates are answerable for invoice content, and a receipt that inherited
 * their totals block would print the whole invoice again.
 */
object ReceiptPdf {

    /** A5 portrait at 72dpi: half a sheet, which is what a receipt is. */
    private const val PAGE_W = 420
    private const val PAGE_H = 595

    private const val MARGIN = 32f

    const val TAG = "ReceiptPdf"

    /**
     * Writes the receipt and hands it to the share sheet.
     *
     * Shares [InvoicePdf.Outcome] so the screen can report a failure with the
     * same words it uses for an invoice.
     */
    fun share(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): InvoicePdf.Outcome {
        val file = create(context, data, shop, design) ?: return InvoicePdf.Outcome.NotWritten
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "receipt", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Partager le reçu")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            InvoicePdf.Outcome.HandedOff(file)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not hand the receipt to another app", e)
            InvoicePdf.Outcome.NoReceiver(file)
        }
    }

    fun create(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): File? {
        val payment = data.payment
        if (payment == null || payment.paidAmount <= 0.0) return null
        return try {
        val printShop = with(InvoicePdf) { data.printedShop(context, shop) }
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "recu_${data.number}.pdf")

        file.outputStream().use { out ->
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            draw(page.canvas, data, printShop, design)
            doc.finishPage(page)
            doc.writeTo(out)
            doc.close()
        }
        android.util.Log.i(TAG, "receipt written ${file.name} ${file.length()} bytes")
        file
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not write the receipt PDF", e)
            null
        }
    }

    private fun draw(
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ) {
        canvas.drawColor(Color.WHITE)
        val width = (PAGE_W - MARGIN * 2)
        val right = PAGE_W - MARGIN
        var y = MARGIN

        val title = PdfText.paint(18f, Color.BLACK, bold = true)
        val name = PdfText.paint(14f, Color.BLACK, bold = true)
        val body = PdfText.paint(9f, Color.BLACK)
        val small = PdfText.paint(8f, Color.GRAY)
        val label = PdfText.paint(7.5f, Color.GRAY, bold = true)

        // ---- who is issuing
        PdfText.draw(canvas, shop.name.ifBlank { "—" }, MARGIN, y, name, width.toInt(), maxLines = 2)
        y += PdfText.height(shop.name, name, width.toInt(), 2) + 2f
        val contact = buildList {
            if (shop.address.isNotBlank()) add(shop.address)
            if (design.showLegalIdentifiers) {
                if (shop.ice.isNotBlank()) add("ICE : ${shop.ice}")
                if (shop.ifNumber.isNotBlank()) add("IF : ${shop.ifNumber}")
                if (shop.rc.isNotBlank()) add("RC : ${shop.rc}")
            }
        }.joinToString(" · ")
        if (contact.isNotEmpty()) {
            PdfText.draw(canvas, contact, MARGIN, y, small, width.toInt(), maxLines = 2)
            y += PdfText.height(contact, small, width.toInt(), 2) + 6f
        }

        val accent = design.accentColor
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            strokeWidth = 2.5f
        }
        canvas.drawLine(MARGIN, y, right, y, rule)
        y += 14f

        // ---- what this is
        PdfText.draw(canvas, "REÇU", MARGIN, y, title, (width * 0.6f).toInt(), maxLines = 1)
        val numberText = "N° R-${data.number}"
        PdfText.draw(
            canvas, numberText, MARGIN, y + 2f, small, (width * 0.6f).toInt(),
            android.text.Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1,
        )
        y += PdfText.height("REÇU", title, width.toInt(), 1) + 6f

        val payment = data.payment ?: return
        val paidOn = payment.paidAt.trim().ifBlank { InvoicePdf.today() }
        val how = payment.method?.french ?: data.paymentMethod.french

        // ---- the sum received
        val intro = "Reçu de ${data.client.name.ifBlank { "—" }}, la somme de :"
        PdfText.draw(canvas, intro, MARGIN, y, body, width.toInt(), maxLines = 2)
        y += PdfText.height(intro, body, width.toInt(), 2) + 2f
        // A business receipt is filed with the invoice it settles, so the
        // client's ICE belongs here too — it is how the two are matched.
        if (data.client.ice.isNotBlank()) {
            val ice = "ICE : ${data.client.ice}"
            PdfText.draw(canvas, ice, MARGIN, y, small, width.toInt(), maxLines = 1)
            y += PdfText.height(ice, small, width.toInt(), 1) + 4f
        }

        // [PdfText.money] already carries the currency and the thousands space.
        val amount = PdfText.money(payment.paidAmount)
        val money = PdfText.paint(22f, accent, bold = true)
        PdfText.draw(canvas, amount, MARGIN, y, money, width.toInt(), maxLines = 1)
        y += PdfText.height(amount, money, width.toInt(), 1) + 2f

        val words = AmountWords.amount(payment.paidAmount)
        PdfText.draw(canvas, words, MARGIN, y, small, width.toInt(), maxLines = 3)
        y += PdfText.height(words, small, width.toInt(), 3) + 10f

        // ---- what it settles
        val forWhat = "En règlement de la ${data.header.lowercase()} N° ${data.number} du ${data.date}."
        PdfText.draw(canvas, forWhat, MARGIN, y, body, width.toInt(), maxLines = 2)
        y += PdfText.height(forWhat, body, width.toInt(), 2) + 4f

        val methodLine = buildString {
            append("Règlement : ")
            append(how)
            append(" le ")
            append(paidOn)
            if (payment.note.isNotBlank()) {
                append(" — Réf. ")
                append(payment.note.trim())
            }
        }
        PdfText.draw(canvas, methodLine, MARGIN, y, body, width.toInt(), maxLines = 2)
        y += PdfText.height(methodLine, body, width.toInt(), 2) + 16f

        // ---- the three figures the customer checks
        val boxTop = y
        val boxH = 46f
        canvas.drawRect(
            MARGIN, boxTop, right, boxTop + boxH,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.LTGRAY
            },
        )
        val values = listOf(
            "TOTAL DE LA FACTURE" to PdfText.money(data.total),
            "TOTAL PAYÉ" to PdfText.money(data.paidAmount),
            "RESTE À PAYER" to PdfText.money(data.balanceDue),
        )
        val cellW = width / 3f
        values.forEachIndexed { index, (caption, value) ->
            val x = MARGIN + cellW * index
            PdfText.draw(canvas, caption, x + 8f, boxTop + 9f, label, (cellW - 16).toInt(), maxLines = 1)
            PdfText.draw(canvas, value, x + 8f, boxTop + 22f, body, (cellW - 16).toInt(), maxLines = 1)
        }
        y = boxTop + boxH + 18f

        if (data.paymentState == PaymentState.PAID) {
            PdfText.draw(
                canvas, "Document soldé.", MARGIN, y, body, width.toInt(), maxLines = 1,
            )
            y += 14f
        }

        // ---- signature, low on the sheet where a stamp goes
        PdfText.draw(
            canvas, "Signature et cachet", right - 120f, PAGE_H - 64f, small, 120,
            android.text.Layout.Alignment.ALIGN_CENTER, maxLines = 1,
        )
        canvas.drawLine(
            right - 120f, PAGE_H - 60f, right, PAGE_H - 60f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 0.8f
                color = Color.LTGRAY
            },
        )

        val footer = "${shop.name.ifBlank { "—" }} · reçu établi le ${InvoicePdf.today()}"
        PdfText.draw(
            canvas, footer, MARGIN, PAGE_H - MARGIN, small, width.toInt(),
            android.text.Layout.Alignment.ALIGN_CENTER, maxLines = 1,
        )
    }

}
