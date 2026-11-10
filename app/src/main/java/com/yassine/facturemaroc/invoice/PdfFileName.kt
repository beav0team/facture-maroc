package com.yassine.facturemaroc.invoice

/**
 * Turns the shop's file-name pattern into a name every share target accepts.
 *
 * A phone's file system, a mail attachment and a USB stick all reject a
 * different subset of characters, and a shop can type anything: `Facture du
 * client / mars` is a name that means a folder to one of them and a broken
 * header to another. Everything unsafe becomes `_`, the extension is always
 * `.pdf`, and a pattern that leaves nothing usable falls back to the default
 * rather than producing a file called `.pdf`.
 *
 * Pure Kotlin, so the rule is tested on the JVM rather than by trying to share
 * a file on a device.
 */
object PdfFileName {

    /** Placeholders, in the order the Settings hint lists them. */
    const val DEFAULT_PATTERN: String = "facture_{number}"

    /** Long enough for a document number, short enough for a mail attachment. */
    private const val MAX_NAME = 90

    /** Characters no common file system or mail client accepts in a name. */
    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /**
     * Expands [pattern] for this document and returns a safe `*.pdf` name.
     *
     * A blank pattern, or one that sanitises to nothing, becomes the default
     * pattern with the same values: the shop gets a usable file, not a name
     * that says the pattern was wrong.
     */
    fun name(
        pattern: String,
        number: String,
        client: String = "",
        date: String = "",
        type: String = "",
    ): String {
        val expanded = expand(pattern.ifBlank { DEFAULT_PATTERN }, number, client, date, type)
        val safe = sanitise(expanded)
        if (safe.isNotBlank()) return safe
        return sanitise(expand(DEFAULT_PATTERN, number, client, date, type))
            .ifBlank { "document.pdf" }
    }

    /**
     * Makes [base] a safe file name ending in `.pdf`.
     *
     * For the receipt and the statement, which have their own base names but
     * must land on the same rules as an invoice.
     */
    fun sanitise(base: String): String {
        val withoutPdf = base.trim().removeSuffix(".pdf").removeSuffix(".PDF")
        val clean = withoutPdf
            .replace(FORBIDDEN, "_")
            // A leading dot hides the file on every Unix-like system, and `..`
            // is a directory traversal in a name the shop typed.
            .trim()
            .trimStart('.')
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim('_', ' ', '.')
            .take(MAX_NAME)
            .trimEnd(' ', '.', '_')
        return if (clean.isBlank()) "" else "$clean.pdf"
    }

    private fun expand(
        pattern: String,
        number: String,
        client: String,
        date: String,
        type: String,
    ): String = pattern
        .replace("{number}", number)
        .replace("{client}", client)
        .replace("{date}", date)
        .replace("{type}", type)
}
