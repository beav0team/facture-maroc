package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The document designs, in the order the picker shows them.
 *
 * These differ in how they print, not only in colour. A shop that photocopies
 * every invoice in black and white, a shop that hands out carbon-copy bons de
 * commande and a shop with an 80mm till roll need genuinely different
 * documents, and a coloured template run through a mono printer is a grey slab
 * that reads worse than a template drawn for paper in the first place.
 *
 * The id is what is stored; the order can change and the enum can grow without
 * invalidating a saved design.
 */
enum class InvoiceTemplate(val id: String, val label: String, val isRoll: Boolean) {
    CLASSIQUE("classique", "Classique", false),
    MODERNE("moderne", "Moderne", false),
    MAROC("maroc", "Facture Maroc", false),
    EPURE("epure", "Épuré", false),
    BANDEAU("bandeau", "Bandeau", false),
    TICKET("ticket", "Ticket (80mm)", true),
    ;

    companion object {
        fun fromId(id: String?): InvoiceTemplate = entries.firstOrNull { it.id == id } ?: CLASSIQUE
    }
}

/**
 * The knobs that change how an invoice looks without changing what it says.
 *
 * The defaults are the ones that make a document acceptable as evidence, so the
 * switches exist to turn detail *off* only deliberately: [showAmountWords] is on
 * because it is the line that makes the total hard to alter by hand, and
 * [showLegalIdentifiers] is on because the numbers that identify the shop are
 * what make it a real invoice rather than a receipt.
 */
data class InvoiceDesign(
    val template: InvoiceTemplate = InvoiceTemplate.CLASSIQUE,
    val logoEnabled: Boolean = true,
    /** Accent as `#RRGGBB`, used by the templates that use a colour at all. */
    val accent: String = DEFAULT_ACCENT,
    val showAmountWords: Boolean = true,
    val showLegalIdentifiers: Boolean = true,
    val showPaymentMethod: Boolean = true,
    val showFooter: Boolean = true,
) {
    /**
     * The accent as a packed colour, falling back to the default when the
     * stored value is malformed.
     *
     * Parsed by hand rather than with `Color.parseColor` so the design model
     * stays plain Kotlin and can be checked by a JVM test — this value decides
     * the ink on a customer's invoice, and a bad preference must degrade to a
     * blue document rather than crash the renderer.
     */
    val accentColor: Int
        get() {
            val hex = accent.trim().removePrefix("#")
            if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) {
                return DEFAULT_ACCENT_COLOR
            }
            return (0xFF000000L or hex.toLong(16)).toInt()
        }

    companion object {
        const val DEFAULT_ACCENT = "#0B57D0"
        const val DEFAULT_ACCENT_COLOR = 0xFF0B57D0.toInt()

        /** The accents offered in the design screen, in warm-to-cool order. */
        val ACCENT_PRESETS: List<String> = listOf(
            "#0B57D0", // bleu
            "#00696E", // sarcelle
            "#3F5B25", // olive
            "#146C2E", // vert
            "#8A5300", // ambre
            "#B3261E", // rouge
            "#A03D62", // rose
            "#6A3FB5", // violet
        )

        fun default(): InvoiceDesign = InvoiceDesign()
    }
}

/** Persisted invoice design — template, logo, accent and the print switches. */
class InvoiceDesignStore(context: Context) {

    private val _design = MutableStateFlow(load(context))
    val design: StateFlow<InvoiceDesign> = _design.asStateFlow()

    fun read(context: Context): InvoiceDesign = load(context)

    fun write(context: Context, design: InvoiceDesign) {
        persist(context, design)
        _design.value = design
    }

    private fun load(context: Context): InvoiceDesign {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return InvoiceDesign(
            template = InvoiceTemplate.fromId(prefs.getString("template", InvoiceTemplate.CLASSIQUE.id)),
            logoEnabled = prefs.getBoolean("logoEnabled", true),
            accent = prefs.getString("accent", InvoiceDesign.DEFAULT_ACCENT)
                ?: InvoiceDesign.DEFAULT_ACCENT,
            showAmountWords = prefs.getBoolean("showAmountWords", true),
            showLegalIdentifiers = prefs.getBoolean("showLegalIdentifiers", true),
            showPaymentMethod = prefs.getBoolean("showPaymentMethod", true),
            showFooter = prefs.getBoolean("showFooter", true),
        )
    }

    fun saveLogo(context: Context, bytes: ByteArray): Boolean {
        val file = logoFile(context)
        return runCatching {
            file.writeBytes(bytes)
            true
        }.getOrElse { false }
    }

    fun removeLogo(context: Context) {
        val file = logoFile(context)
        if (file.isFile) file.delete()
    }

    fun logoFile(context: Context): File = File(context.filesDir, "shop_logo.png")

    companion object {
        private const val PREFS = "invoice_design"

        private fun persist(context: Context, design: InvoiceDesign) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("template", design.template.id)
                .putBoolean("logoEnabled", design.logoEnabled)
                .putString("accent", design.accent)
                .putBoolean("showAmountWords", design.showAmountWords)
                .putBoolean("showLegalIdentifiers", design.showLegalIdentifiers)
                .putBoolean("showPaymentMethod", design.showPaymentMethod)
                .putBoolean("showFooter", design.showFooter)
                .apply()
        }

        fun write(context: Context, design: InvoiceDesign) = persist(context, design)
    }
}
