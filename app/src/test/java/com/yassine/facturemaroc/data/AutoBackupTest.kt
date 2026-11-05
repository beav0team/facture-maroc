package com.yassine.facturemaroc.data

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The daily copy: when it is due, what it is called, and what gets deleted.
 *
 * The point of the feature is the morning the good file is unreadable, so the
 * tests here are about the things that would make that morning worse: a clock
 * that jumps, snapshots that overwrite each other, or pruning that removes the
 * only copy.
 */
class AutoBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `the first run is due and the next one waits a day`() {
        assertTrue(AutoBackup.due(null, 1_000L))
        assertTrue(AutoBackup.due(0L, 1_000L))
        assertFalse(AutoBackup.due(1_000L, 1_000L + day - 1))
        assertTrue(AutoBackup.due(1_000L, 1_000L + day))
    }

    @Test
    fun `a clock that moved backwards does not stop the backups`() {
        // A phone set to last year would otherwise wait a year.
        assertTrue(AutoBackup.due(day * 10, 1_000L))
    }

    @Test
    fun `names sort as dates and carry the prefix pruning looks for`() {
        val early = AutoBackup.snapshotName(1_000L)
        val late = AutoBackup.snapshotName(1_000L + day)

        assertTrue(early.startsWith(AutoBackup.PREFIX))
        assertTrue(early < late)
        assertTrue(AutoBackup.mirrorName(1_000L).startsWith(AutoBackup.MIRROR_PREFIX))
    }

    @Test
    fun `writing leaves no temporary file behind`() {
        val store = SnapshotStore(temp.newFolder("backups"))

        store.write("{}", 1_000L)

        val files = File(temp.root, "backups").listFiles().orEmpty().map { it.name }
        assertEquals(1, files.size)
        assertTrue(files.single().endsWith(".json"))
    }

    @Test
    fun `pruning keeps the newest and deletes the oldest`() {
        val store = SnapshotStore(temp.newFolder("backups"))
        repeat(9) { index -> store.write("{}", 1_000L + index * 1_000L) }

        store.prune(keep = 7)

        val left = store.list()
        assertEquals(7, left.size)
        // Newest first: the ninth write is the one that must survive.
        assertEquals(1_000L + 8 * 1_000L, left.first().takenAt)
    }

    @Test
    fun `the newest copy survives even when it alone is over the ceiling`() {
        val store = SnapshotStore(temp.newFolder("backups"))
        store.write("x".repeat(200), 1_000L)
        store.write("y".repeat(200), 2_000L)

        store.prune(keep = 7, maxBytes = 100)

        val left = store.list()
        assertEquals(1, left.size)
        assertEquals(2_000L, left.single().takenAt)
    }

    @Test
    fun `an unreadable snapshot says so instead of throwing`() {
        val store = SnapshotStore(temp.newFolder("backups"))
        val snapshot = store.write("this is not a backup", 1_000L)

        val outcome = store.read(snapshot)

        assertTrue(outcome is BackupReadOutcome.Failed)
    }
}
