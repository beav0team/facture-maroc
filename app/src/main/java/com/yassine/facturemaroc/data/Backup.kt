package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

/** A file that is not a backup this app can safely restore. */
class BackupException(message: String) : Exception(message)

/**
 * Everything the shop owns, in one JSON file.
 *
 * The point of a backup is a new phone, so it contains the things a new phone
 * cannot fetch again: the shop's identity, its clients, its saved items, every
 * document and the number counters. The logo travels with it as Base64 because
 * it lives in a file rather than in settings.
 *
 * The format is deliberately not a database dump. It is readable JSON with the
 * same field names the app uses, one `format` tag so a random JSON file cannot
 * be mistaken for a backup, and a version number so a file from a future build
 * is refused instead of half-read.
 */
object Backup {

    const val APP = "facture-maroc"
    const val FORMAT = "backup"
    const val VERSION = 1

    /** Refuse absurdly large files before parsing them into memory. */
    const val MAX_BYTES = 8 * 1024 * 1024

    private const val MAX_LOGO_BYTES = 4 * 1024 * 1024

    /** A backup ready to write, and whether its logo made it in. */
    data class Built(
        val json: String,
        val logoIncluded: Boolean,
    )

    /**
     * The whole shop as JSON, with no size limit.
     *
     * Used by a restore test and by [buildForExport], which is the only caller
     * that writes a file. Kept uncapped so a test can build a small backup
     * without going near the file system.
     */
    fun build(
        shop: ShopSettings,
        design: InvoiceDesign,
        logo: ByteArray?,
        clients: List<InvoiceClient>,
        items: List<SavedItem>,
        invoices: List<InvoiceRecord>,
        counters: Map<String, Int>,
        folders: List<String> = emptyList(),
        exportedAt: Long = System.currentTimeMillis(),
    ): String = JSONObject().apply {
        put("app", APP)
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("shop", shopToJson(shop))
        if (logo == null) {
            put("logo", JSONObject.NULL)
        } else {
            put("logo", Base64.getEncoder().encodeToString(logo))
        }
        put(
            "design",
            JSONObject().apply {
                put("template", design.template.id)
                put("logoEnabled", design.logoEnabled)
                put("accent", design.accent)
                put("showAmountWords", design.showAmountWords)
                put("showLegalIdentifiers", design.showLegalIdentifiers)
                put("showPaymentMethod", design.showPaymentMethod)
                put("showFooter", design.showFooter)
            },
        )
        put(
            "counters",
            JSONObject().apply {
                DocumentType.entries.forEach { type -> put(type.id, counters[type.id] ?: 1) }
            },
        )
        put(
            "clients",
            JSONArray().apply { clients.forEach { put(InvoiceJson.clientToJson(it)) } },
        )
        put(
            "folders",
            JSONArray().apply { folders.forEach { put(it) } },
        )
        put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("label", item.label)
                            put("price", item.price)
                            put("unit", item.unit)
                            put("folder", item.folder)
                            put("barcode", item.barcode)
                            put("purchasePrice", item.purchasePrice)
                            // Absent rather than null for a service, so a backup
                            // stays readable by an older build of the app.
                            item.stock?.let { put("stock", it) }
                        },
                    )
                }
            },
        )
        put(
            "invoices",
            JSONArray().apply { invoices.forEach { put(InvoiceJson.recordToJson(it)) } },
        )
    }.toString(2)

    /**
     * The backup to write to a file, never larger than [read] will accept.
     *
     * The cap used to be enforced on import only, so a shop with a big logo
     * could export a file that its own restore then refused — the backup looked
     * done and was worth nothing. The logo is the only part that can be left out
     * without losing a document, so an oversized payload is rebuilt without it
     * and [Built.logoIncluded] says so. When even that does not fit, the file is
     * refused here rather than written broken.
     */
    fun buildForExport(
        shop: ShopSettings,
        design: InvoiceDesign,
        logo: ByteArray?,
        clients: List<InvoiceClient>,
        items: List<SavedItem>,
        invoices: List<InvoiceRecord>,
        counters: Map<String, Int>,
        folders: List<String> = emptyList(),
        exportedAt: Long = System.currentTimeMillis(),
    ): Built {
        fun render(withLogo: ByteArray?) = build(
            shop = shop,
            design = design,
            logo = withLogo,
            clients = clients,
            items = items,
            invoices = invoices,
            counters = counters,
            folders = folders,
            exportedAt = exportedAt,
        )

        val full = render(logo)
        if (full.sizeInBytes() <= MAX_BYTES) return Built(full, logoIncluded = logo != null)
        if (logo == null) throw TooLargeException(TOO_LARGE)
        val trimmed = render(null)
        if (trimmed.sizeInBytes() > MAX_BYTES) throw TooLargeException(TOO_LARGE)
        return Built(trimmed, logoIncluded = false)
    }

    /** The shop's data does not fit in a file this app would accept back. */
    class TooLargeException(message: String) : Exception(message)

    /** What the import cap is measured against, so the two agree. */
    private fun String.sizeInBytes(): Int = toByteArray(Charsets.UTF_8).size

    private const val TOO_LARGE =
        "Cette sauvegarde dépasserait 8 Mo même sans le logo. Elle n'a pas été écrite : " +
            "envoyez plutôt un export des factures par lots."

    /**
     * Reads a file, refusing anything that is not a complete backup.
     *
     * The checks happen here rather than in the screen so the rules are tested
     * without Android, and so a restore can never start from a file that only
     * looked right at the first line.
     */
    fun parse(json: String): BackupData {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw BackupException("Ce fichier n'est pas un fichier JSON valide.")
        }
        if (root.optString("app") != APP) {
            throw BackupException("Ce fichier n'a pas été créé par Facture Maroc.")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupException("Ce fichier n'est pas une sauvegarde Facture Maroc.")
        }
        val version = root.optInt("version", 0)
        if (version <= 0) {
            throw BackupException("La sauvegarde n'indique pas sa version.")
        }
        if (version > VERSION) {
            throw BackupException("Cette sauvegarde vient d'une version plus récente de l'application.")
        }
        val shopObject = root.optJSONObject("shop")
            ?: throw BackupException("La sauvegarde ne contient pas les paramètres du magasin.")

        val logo = if (root.isNull("logo")) {
            null
        } else {
            val encoded = root.optString("logo", "")
            val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrElse {
                throw BackupException("Le logo de la sauvegarde est illisible.")
            }
            if (bytes.size > MAX_LOGO_BYTES) {
                throw BackupException("Le logo de la sauvegarde est trop lourd.")
            }
            bytes.takeIf { it.isNotEmpty() }
        }

        val designObject = root.optJSONObject("design")
        val countersObject = root.optJSONObject("counters")
        val counters = DocumentType.entries.associate { type ->
            type.id to (countersObject?.optInt(type.id, 1) ?: 1).coerceAtLeast(1)
        }

        val clients = root.optJSONArray("clients").mapObjects { InvoiceJson.clientFromJson(it) }
            .filter { it.name.isNotBlank() }
        val folders = root.optJSONArray("folders").mapStrings()
        val items = root.optJSONArray("items").mapObjects { obj ->
            SavedItem(
                label = obj.optString("label", ""),
                price = obj.optDouble("price", 0.0).let { if (it.isNaN()) 0.0 else it },
                unit = obj.optString("unit", ""),
                folder = obj.optString("folder", ""),
                barcode = obj.optString("barcode", ""),
                purchasePrice = obj.optDouble("purchasePrice", 0.0).let { if (it.isNaN()) 0.0 else it },
                // A backup written before the inventory has no quantity, and a
                // restore must not invent one: those items become services with
                // no counted stock, which is what they were.
                stock = if (obj.has("stock") && !obj.isNull("stock")) obj.optInt("stock", 0) else null,
            )
        }.filter { it.label.isNotBlank() && it.price > 0.0 }
        val invoices = root.optJSONArray("invoices").mapObjects(InvoiceJson::recordFromJson)

        return BackupData(
            shop = shopFromJson(shopObject),
            design = InvoiceDesign(
                template = InvoiceTemplate.fromId(designObject?.optString("template")),
                logoEnabled = designObject?.optBoolean("logoEnabled", true) ?: true,
                // A backup written before the switches existed means the
                // defaults, which is the document it was made from.
                accent = designObject?.optString("accent", InvoiceDesign.DEFAULT_ACCENT)
                    ?.takeIf { it.isNotBlank() } ?: InvoiceDesign.DEFAULT_ACCENT,
                showAmountWords = designObject?.optBoolean("showAmountWords", true) ?: true,
                showLegalIdentifiers = designObject?.optBoolean("showLegalIdentifiers", true) ?: true,
                showPaymentMethod = designObject?.optBoolean("showPaymentMethod", true) ?: true,
                showFooter = designObject?.optBoolean("showFooter", true) ?: true,
            ),
            logo = logo,
            clients = clients,
            items = items,
            invoices = invoices,
            counters = counters,
            folders = folders,
            exportedAt = root.optLong("exportedAt", 0L),
        )
    }

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "facture-maroc-sauvegarde-$stamp.json"
    }

    /**
     * Reads a backup from a stream without trusting its size.
     *
     * The cap is enforced while reading rather than after, so a hostile or
     * accidental huge file cannot be pulled into memory first. A file opened
     * through the system picker arrives as a stream; the app never learns its
     * path, which is what makes the permission a per-file one.
     */
    fun read(input: InputStream): BackupReadOutcome {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_BYTES) {
                return BackupReadOutcome.Failed(
                    "Le fichier dépasse 8 Mo. Une sauvegarde Facture Maroc fait quelques kilo-octets.",
                )
            }
            output.write(buffer, 0, read)
        }
        val text = output.toString(Charsets.UTF_8.name())
        return runCatching { parse(text) }
            .fold(
                onSuccess = { BackupReadOutcome.Ready(it) },
                onFailure = { error ->
                    BackupReadOutcome.Failed(
                        (error as? BackupException)?.message
                            ?: "La sauvegarde n'a pas pu être lue.",
                    )
                },
            )
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> =
        if (this == null) {
            emptyList()
        } else {
            (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
        }

    private fun JSONArray?.mapStrings(): List<String> =
        if (this == null) {
            emptyList()
        } else {
            (0 until length()).mapNotNull { index ->
                optString(index, "").trim().takeIf { it.isNotEmpty() }
            }
        }

    private fun shopToJson(shop: ShopSettings): JSONObject = JSONObject().apply {
        put("name", shop.name)
        put("address", shop.address)
        put("phone", shop.phone)
        put("email", shop.email)
        put("ice", shop.ice)
        put("ifNumber", shop.ifNumber)
        put("rc", shop.rc)
        put("patente", shop.patente)
        put("cnss", shop.cnss)
        put("vatPercent", shop.vatPercent)
        // Optional keys: a backup written before these existed simply has
        // neither, and restores to the defaults below.
        put("vatNotApplicable", shop.vatNotApplicable)
        put("pricesIncludeVat", shop.pricesIncludeVat)
    }

    private fun shopFromJson(obj: JSONObject): ShopSettings = ShopSettings(
        name = obj.optString("name", ""),
        address = obj.optString("address", ""),
        phone = obj.optString("phone", ""),
        email = obj.optString("email", ""),
        ice = obj.optString("ice", ""),
        ifNumber = obj.optString("ifNumber", ""),
        rc = obj.optString("rc", ""),
        patente = obj.optString("patente", ""),
        cnss = obj.optString("cnss", ""),
        vatPercent = obj.optDouble("vatPercent", 20.0).let { if (it.isNaN()) 20.0 else it },
        vatNotApplicable = obj.optBoolean("vatNotApplicable", false),
        pricesIncludeVat = obj.optBoolean("pricesIncludeVat", false),
    )
}

/**
 * A parsed backup, before anything on the phone has been touched.
 *
 * Reading and applying are two steps on purpose: the confirmation dialog shows
 * what the file holds, and cancelling it leaves the shop's data exactly as it
 * was.
 */
data class BackupData(
    val shop: ShopSettings,
    val design: InvoiceDesign,
    val logo: ByteArray?,
    val clients: List<InvoiceClient>,
    val items: List<SavedItem>,
    val invoices: List<InvoiceRecord>,
    val counters: Map<String, Int>,
    val folders: List<String> = emptyList(),
    val exportedAt: Long,
)

/** What reading a backup file produced. */
sealed interface BackupReadOutcome {
    data class Ready(val backup: BackupData) : BackupReadOutcome
    data class Failed(val message: String) : BackupReadOutcome
}

/** What writing a backup file produced. */
sealed interface BackupExportOutcome {
    data object Saved : BackupExportOutcome

    /** Written without the logo: with it, the file would not have fitted. */
    data object SavedWithoutLogo : BackupExportOutcome

    /** The shop's data does not fit in a file the app could restore. */
    data class TooLarge(val message: String) : BackupExportOutcome

    data object Failed : BackupExportOutcome
}
