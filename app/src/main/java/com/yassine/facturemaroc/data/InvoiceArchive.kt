package com.yassine.facturemaroc.data

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * One document as it left the shop, and the hash that ties it to the one before.
 *
 * [hash] covers every field of the entry plus [previousHash], so changing a
 * single character of any of them is detectable. [contentSha256] is the hash of
 * the exact bytes that were exported, which is the only way afterwards to tell
 * "this is the file we issued" from "this is a file that looks like it".
 */
data class ArchiveEntry(
    /** 1 for the first document of the shop, then one more each time. */
    val sequence: Int,
    /** ISO 8601 instant, UTC: `2026-10-29T09:14:03Z`. */
    val recordedAt: String,
    /** The document number the customer received. */
    val number: String,
    /** [DocumentType.id], not the French label, so the log survives a relabel. */
    val documentType: String,
    val contentSha256: String,
    val previousHash: String,
    val hash: String,
)

/**
 * What a verification found.
 *
 * [problem] is written for a shop owner, not for a developer: it names the
 * document and says what no longer holds. A false accusation is worse than no
 * check at all, so every branch below is one this code can actually observe.
 */
data class ArchiveVerification(
    val entries: Int,
    val ok: Boolean,
    val problem: String? = null,
) {
    val message: String
        get() = when {
            ok && entries == 0 -> "Aucun document dans l'archive."
            ok -> "$entries document${if (entries > 1) "s" else ""} vérifié${if (entries > 1) "s" else ""}."
            else -> problem ?: "L'archive n'a pas pu être vérifiée."
        }
}

/**
 * The hash chain itself, with no file and no Android in it.
 *
 * Kept separate from [InvoiceArchive] for one reason: this is the part whose
 * correctness is a security claim, and it can be tested exhaustively on the JVM.
 * The file store around it is deliberately thin.
 *
 * The chain is a plain hash chain rather than a signature. Without a key held
 * outside the phone there is nothing to sign with that an attacker could not
 * also fetch, and a signature that can be forged is worse than an honest hash
 * chain: it looks stronger than it is. What this proves is that the sequence was
 * not edited or reordered afterwards *relative to the head the shop has seen*,
 * which is exactly the claim an archive can support on a device the shop owns.
 */
object ArchiveLedger {

    /** The `previousHash` of the first entry: no document came before it. */
    val GENESIS_HASH: String = "0".repeat(64)

    /** Separates fields so that shifting a character cannot forge a match. */
    private const val SEPARATOR = "\u001F"

    private const val HEX = "0123456789abcdef"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    /** Seconds are enough: two documents do not leave the same phone at once. */
    fun timestamp(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))

    /**
     * The hash of an entry, over everything except the hash itself.
     *
     * Fields are joined with a separator rather than concatenated: without it,
     * a number ending in the first digits of the hash would produce the same
     * digest for two different documents.
     */
    fun computeHash(entry: ArchiveEntry): String = sha256Hex(
        listOf(
            entry.sequence.toString(),
            entry.recordedAt,
            entry.number,
            entry.documentType,
            entry.contentSha256,
            entry.previousHash,
        ).joinToString(SEPARATOR),
    )

    /** The entry that follows [entries], linked to the last one. */
    fun append(
        entries: List<ArchiveEntry>,
        number: String,
        documentType: String,
        contentSha256: String,
        recordedAt: String = timestamp(Instant.now()),
    ): ArchiveEntry {
        val draft = ArchiveEntry(
            sequence = (entries.lastOrNull()?.sequence ?: 0) + 1,
            recordedAt = recordedAt,
            number = number,
            documentType = documentType,
            contentSha256 = contentSha256,
            previousHash = entries.lastOrNull()?.hash ?: GENESIS_HASH,
            hash = "",
        )
        return draft.copy(hash = computeHash(draft))
    }

    /**
     * Checks that every entry is intact and follows the one before it.
     *
     * Four separate failures, reported separately because they mean different
     * things: an edit to an entry, a removed entry, a reordered journal, and a
     * journal that no longer parses at all.
     */
    fun verifyChain(entries: List<ArchiveEntry>): ArchiveVerification {
        var previous = GENESIS_HASH
        entries.forEachIndexed { index, entry ->
            if (entry.sequence != index + 1) {
                return ArchiveVerification(
                    entries.size,
                    false,
                    "Le journal saute une entrée : ${entries.size} document(s) enregistré(s), " +
                        "mais le numéro ${entry.sequence} arrive en position ${index + 1}.",
                )
            }
            if (entry.previousHash != previous) {
                return ArchiveVerification(
                    entries.size,
                    false,
                    "L'entrée n° ${index + 1} du journal (document ${entry.number}) ne suit " +
                        "pas celle qui la précède : le journal a été réordonné ou une entrée retirée.",
                )
            }
            if (entry.hash != computeHash(entry)) {
                return ArchiveVerification(
                    entries.size,
                    false,
                    "L'entrée n° ${index + 1} du journal (document ${entry.number}) ne " +
                        "correspond plus à son empreinte : une ligne a été modifiée après coup.",
                )
            }
            previous = entry.hash
        }
        return ArchiveVerification(entries.size, true)
    }

    fun toJson(entry: ArchiveEntry): JSONObject = JSONObject().apply {
        put("v", 1)
        put("sequence", entry.sequence)
        put("recordedAt", entry.recordedAt)
        put("number", entry.number)
        put("documentType", entry.documentType)
        put("contentSha256", entry.contentSha256)
        put("previousHash", entry.previousHash)
        put("hash", entry.hash)
    }

    fun fromJson(obj: JSONObject): ArchiveEntry? = runCatching {
        ArchiveEntry(
            sequence = obj.getInt("sequence"),
            recordedAt = obj.getString("recordedAt"),
            number = obj.getString("number"),
            documentType = obj.getString("documentType"),
            contentSha256 = obj.getString("contentSha256"),
            previousHash = obj.getString("previousHash"),
            hash = obj.getString("hash"),
        )
    }.getOrNull()

    /** One JSON object per line, so a human can read the journal in a text editor. */
    fun toLines(entries: List<ArchiveEntry>): String = buildString {
        entries.forEach { append(toJson(it).toString()).append('\n') }
    }

    /**
     * Reads the journal back, or refuses.
     *
     * A line that will not parse returns `null` rather than being skipped: a
     * skipped line is a hole in the chain, and a hole that is silently ignored
     * makes the whole check meaningless. Whitespace-only lines are tolerated
     * because a trailing newline is not tampering.
     */
    fun parseLines(text: String): List<ArchiveEntry>? {
        val entries = ArrayList<ArchiveEntry>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val entry = runCatching { fromJson(JSONObject(trimmed)) }.getOrNull() ?: return null
            entries += entry
        }
        return entries
    }
}

/**
 * A copy of every exported document, chained so a later edit cannot hide.
 *
 * Three files' worth of structure, under `filesDir/archive/`:
 *
 * - `ledger.jsonl` — one [ArchiveEntry] per document, in issue order.
 * - `documents/<sha256>.xml` — the exact bytes that were written to the file
 *   the user picked, named by their own hash.
 *
 * The XML is copied rather than only hashed, because a hash of a file the shop
 * has since deleted or overwritten proves nothing. Holding the bytes means the
 * archive can still show what was issued after the Downloads folder is empty.
 *
 * Why a copy at all, when the app already keeps every document as JSON: a JSON
 * record is the app's own data and changes when the app changes. The archive is
 * the one place that answers "what did this shop actually send, in the format it
 * sent it". It is also the seam where a real transmission log goes later without
 * touching the invoice model.
 */
class InvoiceArchive(root: File) {

    private val dir = File(root, DIR)
    private val documents = File(dir, "documents")
    private val ledger = File(dir, LEDGER)
    private val lock = Any()

    /** Every entry, oldest first. Empty when the journal is missing or unreadable. */
    fun entries(): List<ArchiveEntry> = synchronized(lock) {
        runCatching { readLedger() }.getOrNull().orEmpty()
    }

    /**
     * Files [xml] under its own hash and links it into the chain.
     *
     * Returns the entry, or `null` when nothing was recorded. It never throws:
     * the caller has already written the user's file and the archive must not be
     * able to take that away. A failure here costs the evidence, not the export.
     *
     * Recording the same bytes twice is a no-op. The archive is a record of
     * documents, not of button presses: re-exporting an old invoice to send it
     * again must not put a second copy in the chain, or a shop that emails the
     * same facture three times ends up with a journal that says three invoices
     * were issued.
     */
    fun record(data: InvoiceData, xml: String, recordedAt: Instant = Instant.now()): ArchiveEntry? {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        val contentHash = ArchiveLedger.sha256Hex(bytes)
        synchronized(lock) {
            return try {
                // An unreadable journal means someone has been at it. Starting a
                // fresh chain from it would overwrite the evidence with a clean
                // one, which is the exact thing this file exists to prevent.
                val existing = readLedger() ?: return null
                existing.firstOrNull { it.contentSha256 == contentHash }?.let { return it }
                val entry = ArchiveLedger.append(
                    entries = existing,
                    number = data.number,
                    documentType = data.documentType.id,
                    contentSha256 = contentHash,
                    recordedAt = ArchiveLedger.timestamp(recordedAt),
                )
                storeDocument(contentHash, bytes)
                writeLedger(existing + entry)
                entry
            } catch (error: Exception) {
                null
            }
        }
    }

    /**
     * Re-checks the chain and every stored document.
     *
     * The chain check is the cheap half; the document check is what catches a
     * file that was replaced after the fact, which a ledger-only audit cannot
     * see.
     */
    fun verify(): ArchiveVerification {
        synchronized(lock) {
            val entries = runCatching { readLedger() }.getOrNull()
                ?: return ArchiveVerification(
                    entries = 0,
                    ok = false,
                    problem = "Le journal de l'archive est illisible : " +
                        "une ligne a été tronquée ou modifiée.",
                )
            val chain = ArchiveLedger.verifyChain(entries)
            if (!chain.ok) return chain

            entries.forEach { entry ->
                val file = File(documents, "${entry.contentSha256}.xml")
                if (!file.isFile) {
                    return ArchiveVerification(
                        entries.size,
                        false,
                        "Le document ${entry.number} manque dans l'archive : " +
                            "son fichier n'est plus là.",
                    )
                }
                val actual = runCatching { ArchiveLedger.sha256Hex(file.readBytes()) }.getOrNull()
                if (actual != entry.contentSha256) {
                    return ArchiveVerification(
                        entries.size,
                        false,
                        "Le fichier du document ${entry.number} ne correspond plus à " +
                            "l'empreinte enregistrée : il a été remplacé.",
                    )
                }
            }
            return chain
        }
    }

    /** Null when the journal exists but no longer parses. */
    private fun readLedger(): List<ArchiveEntry>? {
        if (!ledger.isFile) return emptyList()
        return ArchiveLedger.parseLines(ledger.readText(Charsets.UTF_8))
    }

    private fun writeLedger(entries: List<ArchiveEntry>) {
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("archive: cannot create ${dir.path}")
        }
        writeAtomically(ledger, ArchiveLedger.toLines(entries).toByteArray(Charsets.UTF_8))
    }

    private fun storeDocument(hash: String, bytes: ByteArray) {
        if (!documents.isDirectory && !documents.mkdirs()) {
            throw IOException("archive: cannot create ${documents.path}")
        }
        val target = File(documents, "$hash.xml")
        // The name is the hash, so a file already there is either the same
        // bytes or an already-compromised archive. Overwriting would erase the
        // second case, which verify() is meant to report.
        if (target.isFile) return
        writeAtomically(target, bytes)
    }

    /**
     * Writes beside the target and renames over it, then flushes to the disk.
     *
     * The rename is what makes a crash leave old-or-new rather than half a
     * journal; the sync is what makes a power cut leave the entry at all. A
     * ledger that lost its last line to a dead battery would fail verification
     * on the next launch and look like tampering.
     */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            // rename(2) replaces the target on this app's filesystems; a
            // provider that refuses it is handled rather than retried forever.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("archive: cannot replace ${target.path}")
            }
        }
    }

    private companion object {
        const val DIR = "archive"
        const val LEDGER = "ledger.jsonl"
    }
}
