package com.yassine.facturemaroc.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The copy the app takes of itself, without being asked.
 *
 * A manual export asks the shop to do something on the evening it is tired and
 * an invoice is half typed, and the one time it matters — a phone stolen, a
 * history that will not parse, a document deleted by mistake — the last export
 * is a month old. So the app now writes its own:
 *
 * - one snapshot a day, in the app's private `files/backups`, restorable from
 *   the settings screen with no permission and no picker. This is the copy that
 *   survives the fault the manual export cannot catch in time: `invoices.json`
 *   coming back unreadable;
 * - the last few are kept, not just the newest, so a day that went wrong can be
 *   stepped back one snapshot rather than to nothing.
 *
 * The file is the same JSON a manual backup is, made by the same [Backup]
 * builder, so a restore path, a test and a support answer all stay one thing.
 * The cap is the import cap too: a snapshot that the app would refuse to read
 * back is not a backup.
 *
 * What it is not: an off-device copy. That one is [AutoBackupFolder], a folder
 * the shop picks once, written through the system's document provider.
 */
object AutoBackup {

    /**
     * A day between snapshots.
     *
     * Shorter would not buy much — a shop writes a few dozen documents a day at
     * most, and restoring yesterday rather than an hour ago costs one re-typed
     * invoice. Longer would let a week of work be the difference.
     */
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** How many daily snapshots stay on the phone. Seven is a week of undo. */
    const val KEEP = 7

    /**
     * And a ceiling for the whole folder, because the logo and a year of
     * invoices can make a snapshot heavy and the phone is not the shop's disk.
     */
    const val MAX_TOTAL_BYTES = 24L * 1024 * 1024

    /** Names sort as dates, which is what makes pruning and picking simple. */
    const val PREFIX = "auto-"

    /** The stamp inside a snapshot name; reading it back must use the same. */
    const val STAMP_PATTERN = "yyyyMMdd-HHmmss"

    /**
     * How many copies stay in the folder the shop picked.
     *
     * Two weeks rather than one week: that folder is the copy that survives a
     * lost phone, and a shop that only notices a problem a fortnight later
     * should still have something to go back to. It is also cheap — the folder
     * is on a disk, not on the phone.
     */
    const val KEEP_MIRRORED = 14

    /** Files the app wrote into a picked folder all start with this. */
    const val MIRROR_PREFIX = "facture-maroc-auto-"

    fun snapshotName(now: Long): String =
        PREFIX + SimpleDateFormat(STAMP_PATTERN, Locale.US).format(Date(now)) + ".json"

    /** The name the same snapshot takes in the folder the shop picked. */
    fun mirrorName(now: Long): String = "facture-maroc-${snapshotName(now)}"

    /**
     * True when it is time to write another one.
     *
     * A clock that moved backwards (a phone set to last year, a timezone that
     * jumped) must not stop the backups for a year, so a stamp in the future is
     * treated as due too.
     */
    fun due(last: Long?, now: Long): Boolean =
        last == null || last <= 0L || now - last >= INTERVAL_MS || now < last
}

/**
 * The snapshots themselves, in one directory.
 *
 * Kept as plain files rather than a list in preferences: a backup that lives in
 * the same file it is meant to protect against is not a backup.
 */
class SnapshotStore(private val dir: File) {

    data class Snapshot(
        val file: File,
        /** When it was written, from the file itself so a restart cannot lose it. */
        val takenAt: Long,
        val bytes: Long,
    )

    /** Newest first. */
    fun list(): List<Snapshot> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith(AutoBackup.PREFIX) && it.name.endsWith(".json") }
            .map { Snapshot(it, takenAt(it), it.length()) }
            .sortedByDescending { it.takenAt }

    /** When the last snapshot was taken, or null if there has never been one. */
    fun latestMillis(): Long? = list().firstOrNull()?.takenAt

    /**
     * Writes [json] under a fresh name, atomically.
     *
     * Temp-then-rename for the same reason the stores do it: a phone that dies
     * halfway through leaves a `.tmp` behind, never a half file wearing the
     * name of a good snapshot.
     */
    fun write(json: String, now: Long = System.currentTimeMillis()): Snapshot {
        if (!dir.isDirectory) dir.mkdirs()
        val target = File(dir, AutoBackup.snapshotName(now))
        val temp = File(dir, "${target.name}.tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return Snapshot(target, takenAt(target), target.length())
    }

    fun delete(snapshot: Snapshot): Boolean = snapshot.file.delete()

    /**
     * When a snapshot was taken, read from its name.
     *
     * The file's own modification time looked equivalent and was not: a phone
     * that restores a filesystem, or two copies written in the same
     * millisecond, could list them in the wrong order — and pruning reads that
     * order to decide what is old enough to delete.
     */
    private fun takenAt(file: File): Long = runCatching {
        SimpleDateFormat(AutoBackup.STAMP_PATTERN, Locale.US)
            .parse(file.name.removePrefix(AutoBackup.PREFIX).removeSuffix(".json"))
            ?.time
    }.getOrNull() ?: file.lastModified()

    /**
     * Drops what is past [keep] or over [maxBytes], oldest first.
     *
     * The newest snapshot is always kept, even if it alone is over the ceiling:
     * deleting it would leave the phone with no automatic copy at all, which is
     * the situation the feature exists to avoid.
     */
    fun prune(keep: Int = AutoBackup.KEEP, maxBytes: Long = AutoBackup.MAX_TOTAL_BYTES) {
        var total = 0L
        list().forEachIndexed { index, snapshot ->
            val fits = total + snapshot.bytes <= maxBytes
            if (index < keep && (fits || index == 0)) {
                total += snapshot.bytes
            } else {
                snapshot.file.delete()
            }
        }
    }

    /**
     * Reads a snapshot back through the same rules as a picked file.
     *
     * The cap is enforced while reading, so a snapshot that somehow grew past
     * it ends as a message rather than as a shop half restored.
     */
    fun read(snapshot: Snapshot): BackupReadOutcome =
        runCatching { snapshot.file.inputStream().use(Backup::read) }
            .getOrElse {
                BackupReadOutcome.Failed("Cette sauvegarde automatique n'a pas pu être lue.")
            }
}
