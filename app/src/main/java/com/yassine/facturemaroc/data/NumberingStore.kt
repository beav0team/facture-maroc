package com.yassine.facturemaroc.data

import android.content.Context

/**
 * How the shop's documents are numbered: a prefix per type, and whether the
 * sequence restarts every January.
 *
 * Kept in its own preferences file, apart from [InvoiceNumber]'s counters, so
 * that reading a prefix never touches a sequence. An absent prefix means the
 * type's own default ([DocumentType.prefix]); a blank prefix means the same
 * thing, which is why "reset to F" stores nothing rather than an empty string.
 *
 * The prefix is part of a file name — a PDF is written as `F-20261110-0001.pdf`
 * — so only letters, digits, `-` and `_` are accepted. A shop that typed a
 * slash would get a document it cannot save.
 */
class NumberingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The prefix a document of [type] will carry, override or default. */
    fun prefix(type: DocumentType): String =
        prefs.getString(keyFor(type), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isValidPrefix(it) }
            ?: type.prefix

    /**
     * Stores the override for [type], or removes it when [value] is blank.
     *
     * A value that cannot be a file name is ignored rather than written: the
     * screen already filters those characters, and a corrupt prefs value must
     * not be able to break numbering.
     */
    fun setPrefix(type: DocumentType, value: String) {
        val clean = value.trim()
        prefs.edit().apply {
            if (clean.isEmpty()) {
                remove(keyFor(type))
            } else if (isValidPrefix(clean)) {
                putString(keyFor(type), clean)
            }
        }.commit()
    }

    /** True when the next sequence restarts on 1 January. */
    fun resetAnnually(): Boolean = prefs.getBoolean(KEY_RESET, false)

    fun setResetAnnually(value: Boolean) {
        prefs.edit().putBoolean(KEY_RESET, value).commit()
    }

    /** Every stored override, for a backup: only the customised types. */
    fun overrides(): Map<String, String> = DocumentType.entries.mapNotNull { type ->
        prefs.getString(keyFor(type), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isValidPrefix(it) }
            ?.let { type.id to it }
    }.toMap()

    /**
     * Replaces the whole numbering configuration from a backup.
     *
     * A missing type is reset to its default, which is what the backup said by
     * not mentioning it.
     */
    fun writeAll(prefixes: Map<String, String>, resetAnnually: Boolean) {
        prefs.edit().apply {
            DocumentType.entries.forEach { type ->
                val value = prefixes[type.id]?.trim()
                if (value.isNullOrEmpty()) {
                    remove(keyFor(type))
                } else if (isValidPrefix(value)) {
                    putString(keyFor(type), value)
                }
            }
            putBoolean(KEY_RESET, resetAnnually)
        }.commit()
    }

    private fun keyFor(type: DocumentType): String = "prefix_${type.id}"

    companion object {
        const val PREFS = "invoice_numbering"

        private const val KEY_RESET = "reset_annually"

        /**
         * Whether [value] can be part of a number that is also a file name.
         *
         * Empty is not a valid *override*; callers treat it as "use the
         * default". Spaces, slashes and dots are refused because they would
         * either break the printed alignment or create a path.
         */
        fun isValidPrefix(value: String): Boolean =
            value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
