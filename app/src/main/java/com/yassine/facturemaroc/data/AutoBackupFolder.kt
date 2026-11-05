package com.yassine.facturemaroc.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * The off-device half of the automatic backup: one folder, chosen once.
 *
 * A snapshot in `files/backups` survives a corrupt history and a mistaken
 * delete, but not an uninstall or a phone at the bottom of a river. Copying it
 * somewhere the shop picks fixes that without the app ever opening a socket:
 * the system's document provider does the writing, and if the shop chose a
 * folder in Drive or on an SD card it is the provider — not this app — that
 * syncs it.
 *
 * The permission is persisted, so the folder is picked once and every daily
 * snapshot lands there afterwards with no dialog. It is a *tree* grant
 * (`ACTION_OPEN_DOCUMENT_TREE`) rather than a file, because a file chosen with
 * `CreateDocument` comes back with a grant the system is free not to keep, and
 * a backup that stops silently after the second day is worse than none.
 *
 * Every method here fails quietly with `false` rather than throwing: a card
 * removed, a folder deleted, a provider that revoked its grant — the shop keeps
 * its local snapshots and the screen shows the folder as unset.
 */
class AutoBackupFolder(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver

    /** The chosen folder, or null when the shop has not picked one. */
    fun tree(): Uri? = prefs.getString(KEY_TREE, null)
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)

    /** What to call it on screen: the folder's own name, as the provider shows it. */
    fun label(): String? = prefs.getString(KEY_LABEL, null)?.takeIf { it.isNotBlank() }

    /**
     * Keeps [uri] for good, if the provider allows it.
     *
     * A provider that does not support a persisted grant (some third-party file
     * managers) returns false here, and the screen says so instead of pretending
     * the folder will be written every day.
     */
    fun remember(uri: Uri): Boolean {
        val granted = runCatching {
            resolver.takePersistableUriPermission(uri, GRANT_FLAGS)
        }.isSuccess
        if (!granted) return false
        prefs.edit()
            .putString(KEY_TREE, uri.toString())
            .putString(KEY_LABEL, displayName(uri))
            .apply()
        return true
    }

    fun forget() {
        tree()?.let { runCatching { resolver.releasePersistableUriPermission(it, GRANT_FLAGS) } }
        prefs.edit().remove(KEY_TREE).remove(KEY_LABEL).apply()
    }

    /**
     * Writes [json] into the folder as [name], replacing a file of that name.
     *
     * Writes are truncating (`wt`): without it a shorter snapshot would leave
     * the tail of the old one behind and the file would no longer parse. Older
     * copies written by this app are pruned to [AutoBackup.KEEP_MIRRORED].
     */
    fun write(name: String, json: String): Boolean {
        val tree = tree() ?: return false
        val parent = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        }.getOrNull() ?: return false
        return runCatching {
            val target = child(tree, name)
                ?: DocumentsContract.createDocument(resolver, parent, MIME, name)
                ?: return@runCatching false
            val stream = resolver.openOutputStream(target, "wt") ?: return@runCatching false
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            prune(tree)
            true
        }.getOrElse { error ->
            Log.w(TAG, "the snapshot could not be written to the chosen folder", error)
            false
        }
    }

    /** A copy in the folder proving the grant still works, for the first pick. */
    fun writeNow(json: String, now: Long = System.currentTimeMillis()): Boolean =
        write(AutoBackup.mirrorName(now), json)

    private fun children(tree: Uri): List<Pair<String, Uri>> = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val found = mutableListOf<Pair<String, Uri>>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val displayName = cursor.getString(1) ?: continue
                found += displayName to DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
        }
        found
    }.getOrDefault(emptyList())

    private fun child(tree: Uri, name: String): Uri? =
        children(tree).firstOrNull { (displayName, _) -> displayName == name }?.second

    /** Keeps the newest copies this app wrote; a shop's other files are left alone. */
    private fun prune(tree: Uri) {
        children(tree)
            .filter { (name, _) ->
                name.startsWith(AutoBackup.MIRROR_PREFIX) && name.endsWith(".json")
            }
            .sortedByDescending { it.first }
            .drop(AutoBackup.KEEP_MIRRORED)
            .forEach { (_, uri) -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
    }

    private fun displayName(tree: Uri): String? = runCatching {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment

    private companion object {
        const val PREFS = "auto_backup"
        const val KEY_TREE = "folder"
        const val KEY_LABEL = "folderLabel"
        const val MIME = "application/json"
        const val TAG = "AutoBackupFolder"

        const val GRANT_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
