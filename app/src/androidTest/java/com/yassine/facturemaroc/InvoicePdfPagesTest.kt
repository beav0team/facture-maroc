package com.yassine.facturemaroc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.invoice.InvoicePdf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How many sheets a real PDF comes out with, counted by the platform's own
 * reader rather than by the code that wrote it.
 *
 * The layout arithmetic is covered by `PageFlowTest` on the JVM. What that
 * cannot prove is that the renderers actually ask for the pages the arithmetic
 * decided on — a template that ignores the result and keeps drawing on page one
 * would pass every unit test and still hand out an invoice with no total. This
 * test is the renderer and the writer agreeing in the same place.
 */
@RunWith(AndroidJUnit4::class)
class InvoicePdfPagesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val shop = ShopSettings(
        name = "Garage Atlas",
        address = "12 rue des Pneus, Casablanca",
        ice = "001234567000089",
        ifNumber = "40218765",
    )

    private val client = InvoiceClient(
        name = "Société BTP Nord",
        address = "Zone industrielle, Tanger",
        ice = "009876543000012",
    )

    private fun invoice(number: String, lineCount: Int): InvoiceData = InvoiceData(
        number = number,
        date = "29/10/2026",
        client = client,
        lines = List(lineCount) { index ->
            InvoiceLine(
                description = "Pneu 205/55 R16 ${index + 1}",
                quantity = 1,
                unitPrice = 950.0 + index,
            )
        },
    )

    private fun write(data: InvoiceData, template: InvoiceTemplate): File {
        val file = InvoicePdf.createPdf(context, data, shop, InvoiceDesign(template = template))
        assertNotNull("no PDF was written for ${template.id}", file)
        return file!!
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }

    /** Non-white pixels on one page, drawn small: enough to tell blank from written. */
    private fun inkOn(file: File, index: Int): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val width = 120
                    val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var ink = 0
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            if (bitmap.getPixel(x, y) != Color.WHITE) ink += 1
                        }
                    }
                    bitmap.recycle()
                    ink
                }
            }
        }

    @Test
    fun aShortInvoiceStaysOnOneSheet() {
        val data = invoice("F-20261029-9001", 3)
        InvoiceTemplate.entries.forEach { template ->
            val file = write(data, template)
            assertEquals("${template.id} split a three-line sale", 1, pageCount(file))
            file.delete()
        }
    }

    @Test
    fun aLongInvoiceGetsASecondSheet() {
        val data = invoice("F-20261029-9002", 40)
        val file = write(data, InvoiceTemplate.CLASSIQUE)
        assertTrue("forty lines did not paginate", pageCount(file) >= 2)
        file.delete()
    }

    @Test
    fun everySheetCarriesWriting() {
        val data = invoice("F-20261029-9003", 40)
        val file = write(data, InvoiceTemplate.CLASSIQUE)
        val pages = pageCount(file)
        for (page in 0 until pages) {
            assertTrue("page ${page + 1} of $pages came out blank", inkOn(file, page) > 100)
        }
        file.delete()
    }

    @Test
    fun theGriddedLayoutPaginatesToo() {
        val data = invoice("F-20261029-9004", 40)
        val file = write(data, InvoiceTemplate.MAROC)
        assertTrue("the gridded layout did not paginate", pageCount(file) >= 2)
        file.delete()
    }

    @Test
    fun everySheetOfEveryLayoutCarriesWriting() {
        // Each A4 template decides for itself when to call newPage and what to
        // draw at the top of the next one. A layout that paginates but leaves
        // page two blank passes the page count and still hands over a document
        // with no total on it, so the ink is checked on every sheet.
        val data = invoice("F-20261029-9006", 40)
        InvoiceTemplate.entries.filterNot { it.isRoll }.forEach { template ->
            val file = write(data, template)
            val pages = pageCount(file)
            assertTrue("${template.id} did not paginate", pages >= 2)
            for (page in 0 until pages) {
                assertTrue(
                    "${template.id} page ${page + 1} of $pages came out blank",
                    inkOn(file, page) > 100,
                )
            }
            file.delete()
        }
    }

    @Test
    fun theReceiptNeverPaginates() {
        // A roll is one page whatever is on it: its height is computed from the
        // sale before the page is started, so a second page would be a second
        // receipt rather than a continuation.
        val data = invoice("T-20261029-9005", 40)
        val file = write(data, InvoiceTemplate.TICKET)
        assertEquals(1, pageCount(file))
        file.delete()
    }
}
