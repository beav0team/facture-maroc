package com.yassine.facturemaroc.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * What the archive can prove, and what it must never do.
 *
 * The chain logic is tested on its own because "the journal cannot be edited
 * afterwards" is the only reason this file exists. The file store is tested
 * through the same operations a shop performs: record, re-export, delete the
 * exported copy, try to read it back.
 */
class InvoiceArchiveTest {

    private lateinit var root: File
    private lateinit var archive: InvoiceArchive

    private val data = InvoiceData(
        number = "F-20261029-0001",
        date = "29/10/2026",
        client = InvoiceClient("Client de passage"),
        lines = listOf(InvoiceLine("Montage", quantity = 1, unitPrice = 100.0)),
    )

    private fun xmlFor(number: String) =
        "<Invoice><cbc:ID>$number</cbc:ID></Invoice>"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("facture-archive").toFile()
        archive = InvoiceArchive(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    // --- the chain, with no files involved -------------------------------

    @Test
    fun `the first entry starts from the genesis hash`() {
        val entry = ArchiveLedger.append(
            entries = emptyList(),
            number = "F-1",
            documentType = "facture",
            contentSha256 = "a".repeat(64),
            recordedAt = "2026-10-29T10:00:00Z",
        )

        assertEquals(1, entry.sequence)
        assertEquals(ArchiveLedger.GENESIS_HASH, entry.previousHash)
        assertEquals(64, entry.hash.length)
        assertEquals(entry.hash, ArchiveLedger.computeHash(entry))
    }

    @Test
    fun `each entry names the one before it`() {
        val first = ArchiveLedger.append(emptyList(), "F-1", "facture", "a".repeat(64), "2026-10-29T10:00:00Z")
        val second = ArchiveLedger.append(listOf(first), "F-2", "facture", "b".repeat(64), "2026-10-29T10:01:00Z")

        assertEquals(2, second.sequence)
        assertEquals(first.hash, second.previousHash)
    }

    @Test
    fun `changing any field changes the hash`() {
        val entry = ArchiveLedger.append(emptyList(), "F-1", "facture", "a".repeat(64), "2026-10-29T10:00:00Z")

        listOf(
            entry.copy(sequence = 2),
            entry.copy(recordedAt = "2026-10-29T10:00:01Z"),
            entry.copy(number = "F-2"),
            entry.copy(documentType = "devis"),
            entry.copy(contentSha256 = "b".repeat(64)),
            entry.copy(previousHash = "c".repeat(64)),
        ).forEach { changed ->
            assertNotEquals(entry.hash, ArchiveLedger.computeHash(changed))
        }
    }

    @Test
    fun `fields are separated, so shifting a character across a boundary cannot collide`() {
        // number "AB" + type "C" and number "A" + type "BC" concatenate to the
        // same string. With a separator they are two different documents.
        val left = ArchiveEntry(1, "t", "AB", "C", "d".repeat(64), ArchiveLedger.GENESIS_HASH, "")
        val right = ArchiveEntry(1, "t", "A", "BC", "d".repeat(64), ArchiveLedger.GENESIS_HASH, "")

        assertNotEquals(ArchiveLedger.computeHash(left), ArchiveLedger.computeHash(right))
    }

    @Test
    fun `an edited, removed or reordered journal is refused`() {
        val first = ArchiveLedger.append(emptyList(), "F-1", "facture", "a".repeat(64), "2026-10-29T10:00:00Z")
        val second = ArchiveLedger.append(listOf(first), "F-2", "facture", "b".repeat(64), "2026-10-29T10:01:00Z")
        val third = ArchiveLedger.append(listOf(first, second), "F-3", "facture", "c".repeat(64), "2026-10-29T10:02:00Z")
        val chain = listOf(first, second, third)
        assertTrue(ArchiveLedger.verifyChain(chain).ok)

        assertFalse(ArchiveLedger.verifyChain(listOf(first, second.copy(number = "F-9"), third)).ok)
        assertFalse(ArchiveLedger.verifyChain(listOf(first, third)).ok)
        assertFalse(ArchiveLedger.verifyChain(listOf(second, first, third)).ok)
        // A forged entry that carries a recomputed hash still has to link back
        // to the one it claims to follow.
        val forged = second.copy(number = "F-9")
        assertFalse(ArchiveLedger.verifyChain(listOf(first, forged.copy(hash = ArchiveLedger.computeHash(forged)), third)).ok)
    }

    @Test
    fun `an empty journal has nothing to disprove`() {
        val verification = ArchiveLedger.verifyChain(emptyList())

        assertTrue(verification.ok)
        assertEquals(0, verification.entries)
    }

    @Test
    fun `a journal survives being written and read back`() {
        val first = ArchiveLedger.append(emptyList(), "F-1", "facture", "a".repeat(64), "2026-10-29T10:00:00Z")
        val second = ArchiveLedger.append(listOf(first), "A-1", "avoir", "b".repeat(64), "2026-10-29T10:01:00Z")

        val text = ArchiveLedger.toLines(listOf(first, second))

        assertEquals(2, text.lines().count { it.isNotBlank() })
        assertEquals(listOf(first, second), ArchiveLedger.parseLines(text))
    }

    @Test
    fun `a torn line is refused rather than skipped`() {
        val entry = ArchiveLedger.append(emptyList(), "F-1", "facture", "a".repeat(64), "2026-10-29T10:00:00Z")
        val text = ArchiveLedger.toLines(listOf(entry))

        assertNull(ArchiveLedger.parseLines(text.dropLast(6)))
        assertEquals(listOf(entry), ArchiveLedger.parseLines(text + "\n\n  \n"))
    }

    // --- the file store --------------------------------------------------

    @Test
    fun `recording a document keeps a copy of the exact bytes`() {
        val xml = xmlFor("F-20261029-0001")

        val entry = archive.record(data, xml, Instant.parse("2026-10-29T10:00:00Z"))

        assertNotNull(entry)
        assertEquals("2026-10-29T10:00:00Z", entry!!.recordedAt)
        assertEquals("F-20261029-0001", entry.number)
        assertEquals("facture", entry.documentType)
        assertEquals(ArchiveLedger.sha256Hex(xml), entry.contentSha256)

        val copy = File(root, "archive/documents/${entry.contentSha256}.xml")
        assertTrue(copy.isFile)
        assertEquals(xml, copy.readText())
    }

    @Test
    fun `documents chain in the order they were issued`() {
        archive.record(data.copy(number = "F-1"), xmlFor("F-1"), Instant.parse("2026-10-29T10:00:00Z"))
        archive.record(data.copy(number = "F-2"), xmlFor("F-2"), Instant.parse("2026-10-29T10:01:00Z"))
        archive.record(data.copy(number = "F-3"), xmlFor("F-3"), Instant.parse("2026-10-29T10:02:00Z"))

        val entries = archive.entries()

        assertEquals(listOf(1, 2, 3), entries.map { it.sequence })
        assertEquals(listOf("F-1", "F-2", "F-3"), entries.map { it.number })
        assertEquals(ArchiveLedger.GENESIS_HASH, entries.first().previousHash)
        assertEquals(entries[0].hash, entries[1].previousHash)
        assertEquals(entries[1].hash, entries[2].previousHash)
        assertTrue(archive.verify().ok)
    }

    @Test
    fun `sending the same document again does not double the record`() {
        val xml = xmlFor("F-1")

        val first = archive.record(data, xml)
        val second = archive.record(data, xml)

        assertEquals(first, second)
        assertEquals(1, archive.entries().size)
        // ... and it is not a second copy on disk either.
        assertEquals(1, File(root, "archive/documents").listFiles()!!.size)
    }

    @Test
    fun `an edited journal line is caught`() {
        archive.record(data.copy(number = "F-1"), xmlFor("F-1"))
        archive.record(data.copy(number = "F-2"), xmlFor("F-2"))

        val ledger = File(root, "archive/ledger.jsonl")
        ledger.writeText(ledger.readText().replace("\"F-2\"", "\"F-9\""))

        val verification = archive.verify()

        assertFalse(verification.ok)
        // The message names the entry as the journal now reads, not as it was:
        // after an edit, the replaced value is the only one the file still knows.
        assertTrue(verification.problem!!.contains("n° 2"))
        assertTrue(verification.problem!!.contains("F-9"))
    }

    @Test
    fun `a removed line is caught`() {
        archive.record(data.copy(number = "F-1"), xmlFor("F-1"))
        archive.record(data.copy(number = "F-2"), xmlFor("F-2"))
        archive.record(data.copy(number = "F-3"), xmlFor("F-3"))

        val ledger = File(root, "archive/ledger.jsonl")
        val kept = ledger.readLines().filterNot { it.contains("\"F-2\"") }
        ledger.writeText(kept.joinToString("\n") + "\n")

        assertFalse(archive.verify().ok)
    }

    @Test
    fun `a reordered journal is caught`() {
        archive.record(data.copy(number = "F-1"), xmlFor("F-1"))
        archive.record(data.copy(number = "F-2"), xmlFor("F-2"))

        val ledger = File(root, "archive/ledger.jsonl")
        ledger.writeText(ledger.readLines().reversed().joinToString("\n") + "\n")

        assertFalse(archive.verify().ok)
    }

    @Test
    fun `a replaced document is caught even though the journal is untouched`() {
        val entry = archive.record(data, xmlFor("F-1"))!!
        val copy = File(root, "archive/documents/${entry.contentSha256}.xml")
        // Same length, one character different: the forgery a copy-paste allows.
        copy.writeText(xmlFor("F-2"))

        val verification = archive.verify()

        assertFalse(verification.ok)
        assertTrue(verification.problem!!.contains("F-20261029-0001"))
    }

    @Test
    fun `a deleted document is caught`() {
        val entry = archive.record(data, xmlFor("F-1"))!!
        File(root, "archive/documents/${entry.contentSha256}.xml").delete()

        assertFalse(archive.verify().ok)
    }

    @Test
    fun `a journal that no longer parses is never overwritten`() {
        val ledger = File(root, "archive/ledger.jsonl")
        ledger.parentFile!!.mkdirs()
        ledger.writeText("{ this is not a ledger }\n")

        // Recording would otherwise start a clean chain over the evidence.
        assertNull(archive.record(data, xmlFor("F-1")))
        assertEquals("{ this is not a ledger }\n", ledger.readText())
        assertFalse(archive.verify().ok)
    }

    @Test
    fun `an archive with nothing in it verifies and says so`() {
        val verification = archive.verify()

        assertTrue(verification.ok)
        assertEquals(0, verification.entries)
        assertTrue(archive.entries().isEmpty())
        assertEquals("Aucun document dans l'archive.", verification.message)
    }
}
