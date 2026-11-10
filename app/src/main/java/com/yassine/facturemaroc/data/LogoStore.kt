package com.yassine.facturemaroc.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The logos an issued document carries, stored once and referred to by hash.
 *
 * A snapshot used to embed the logo bytes in every invoice record, so a 4 MB
 * logo became ~5 MB of JSON *per invoice* — and the automatic backup keeps
 * seven copies of that file. The bytes do not vary between documents, so they
 * are written once under the SHA-256 of their content and the record keeps the
 * hash. A shop that changes its logo once a year ends up with two files, not
 * one copy per invoice.
 *
 * Files are written temp-then-rename: an interrupted write leaves the previous
 * blob or nothing, never a half image that decodes to grey.
 */
object LogoStore {

    private const val DIR = "logo-snapshots"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    /** Stores [bytes] and returns their hash, or null when they cannot be written. */
    fun put(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val hash = sha256Hex(bytes)
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "$hash.png")
        if (target.isFile && target.length() > 0) return hash
        return runCatching {
            val tmp = File(folder, "$hash.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            hash
        }.getOrNull()
    }

    /** Reads the logo for [hash], or null when it is absent or unreadable. */
    fun read(context: Context, hash: String): ByteArray? {
        if (hash.isBlank()) return null
        val file = File(dir(context), "$hash.png")
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Hashes used by [invoices], for a backup that can reprint them elsewhere. */
    fun hashesOf(invoices: List<InvoiceRecord>): Set<String> =
        invoices.mapNotNull { it.data.shop?.logoHash?.takeIf(String::isNotBlank) }.toSet()

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append("0123456789abcdef"[value ushr 4]).append("0123456789abcdef"[value and 0x0F])
        }
        return out.toString()
    }
}
