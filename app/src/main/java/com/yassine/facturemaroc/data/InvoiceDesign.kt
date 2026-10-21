package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The design of an invoice: which template and whether the logo shows.
 */
data class InvoiceDesign(
    val template: InvoiceTemplate = InvoiceTemplate.CLASSIQUE,
    val logoEnabled: Boolean = true,
) {
    companion object {
        fun default(): InvoiceDesign = InvoiceDesign()
    }
}

enum class InvoiceTemplate(val id: String, val label: String, val isRoll: Boolean) {
    CLASSIQUE("classique", "Classique", false),
    MODERNE("moderne", "Moderne", false),
    MAROC("maroc", "Facture Maroc", false),
    TICKET("ticket", "Ticket (80mm)", true),
    ;

    companion object {
        fun fromId(id: String?): InvoiceTemplate = entries.firstOrNull { it.id == id } ?: CLASSIQUE
    }
}

/** Persisted invoice design — template choice and logo on/off. */
class InvoiceDesignStore(context: Context) {

    private val _design = MutableStateFlow(load(context))
    val design: StateFlow<InvoiceDesign> = _design.asStateFlow()

    fun read(context: Context): InvoiceDesign = load(context)

    fun write(context: Context, design: InvoiceDesign) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("template", design.template.id)
            putBoolean("logoEnabled", design.logoEnabled)
            apply()
        }
        _design.value = design
    }

    private fun load(context: Context): InvoiceDesign {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return InvoiceDesign(
            template = InvoiceTemplate.fromId(prefs.getString("template", "classique")),
            logoEnabled = prefs.getBoolean("logoEnabled", true),
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

    fun logoFile(context: Context): java.io.File =
        java.io.File(context.filesDir, "shop_logo.png")

    companion object {
        private const val PREFS = "invoice_design"

        fun write(context: Context, design: InvoiceDesign) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("template", design.template.id)
                putBoolean("logoEnabled", design.logoEnabled)
                apply()
            }
        }
    }
}
