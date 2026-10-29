package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a folder path behaves.
 *
 * Folders nest, and a folder is stored as one string — "Pneus/Hiver" — rather
 * than as a node with an id and a parent. That keeps the file readable and lets
 * every catalogue written before nesting existed load unchanged, but it moves
 * the whole tree into string rules: what is one level up, what is below, what is
 * a sibling that merely starts with the same letters.
 *
 * Those rules decide which articles a folder shows, what a delete moves up, and
 * what a rename has to rewrite, so they are checked here rather than discovered
 * on the phone.
 */
class FolderPathTest {

    private val tyre = SavedItem(label = "Pneu hiver", price = 950.0, folder = "Pneus/Hiver")
    private val summer = SavedItem(label = "Pneu été", price = 800.0, folder = "Pneus")
    private val oil = SavedItem(label = "Vidange", price = 300.0, folder = "Entretien")
    private val loose = SavedItem(label = "Montage", price = 50.0)
    private val all = listOf(tyre, summer, oil, loose)

    @Test
    fun `a path is split into its parent and its own name`() {
        assertEquals("Pneus", SavedItemStore.parentOf("Pneus/Hiver"))
        assertEquals("Hiver", SavedItemStore.nameOf("Pneus/Hiver"))
    }

    @Test
    fun `a top-level folder has no parent and keeps its name`() {
        assertEquals("", SavedItemStore.parentOf("Pneus"))
        assertEquals("Pneus", SavedItemStore.nameOf("Pneus"))
    }

    @Test
    fun `joining into the root is the name itself`() {
        assertEquals("Pneus", SavedItemStore.join("", "Pneus"))
        assertEquals("Pneus/Hiver", SavedItemStore.join("Pneus", "Hiver"))
    }

    @Test
    fun `a path is drawn with arrows, not slashes`() {
        assertEquals("Pneus › Hiver", SavedItemStore.display("Pneus/Hiver"))
        assertEquals("Pneus", SavedItemStore.display("Pneus"))
    }

    @Test
    fun `a folder is inside itself and inside everything above it`() {
        assertTrue(SavedItemStore.isIn("Pneus/Hiver", "Pneus"))
        assertTrue(SavedItemStore.isIn("Pneus/Hiver/Clouté", "Pneus"))
        assertTrue(SavedItemStore.isIn("Pneus", "Pneus"))
        assertTrue(SavedItemStore.isIn("pneus/hiver", "Pneus"))
    }

    @Test
    fun `a sibling that only starts with the same letters is not inside`() {
        // The trap a plain startsWith falls into: deleting "Pneus" would take
        // "Pneus d'hiver" with it, and the shop would lose a folder it never
        // named.
        assertFalse(SavedItemStore.isIn("Pneus d'hiver", "Pneus"))
        assertFalse(SavedItemStore.isIn("Entretien", "Pneus"))
    }

    @Test
    fun `nothing is inside Sans dossier`() {
        // "" matches no prefix, so the unfiled shelf cannot swallow the tree.
        assertFalse(SavedItemStore.isIn("Pneus", ""))
        assertTrue(SavedItemStore.isIn("", ""))
    }

    @Test
    fun `renaming rewrites the path and keeps everything below it`() {
        assertEquals("Pneus/Roues", SavedItemStore.rebase("Pneus", "Pneus", "Pneus/Roues"))
        assertEquals("Pneus/Roues/Hiver", SavedItemStore.rebase("Pneus/Hiver", "Pneus", "Pneus/Roues"))
    }

    @Test
    fun `renaming leaves the rest of the tree alone`() {
        assertEquals("Entretien", SavedItemStore.rebase("Entretien", "Pneus", "Pneus/Roues"))
    }

    @Test
    fun `only the direct children of a folder are listed`() {
        val folders = listOf("Pneus", "Pneus/Hiver", "Pneus/Hiver/Clouté", "Entretien")

        assertEquals(
            listOf("Entretien", "Pneus"),
            SavedItemStore.children("", folders),
        )
        assertEquals(
            listOf("Pneus/Hiver"),
            SavedItemStore.children("Pneus", folders),
        )
    }

    @Test
    fun `children are sorted by their own name, not by their stored path`() {
        val folders = listOf("Pneus/Zeta", "Pneus/alpha", "Pneus/Beta")

        assertEquals(
            listOf("Pneus/alpha", "Pneus/Beta", "Pneus/Zeta"),
            SavedItemStore.children("Pneus", folders),
        )
    }

    @Test
    fun `a sub-folder always comes with the folder that holds it`() {
        // Otherwise the grid, which only draws children of the folder it stands
        // in, would hold a shelf nobody can reach.
        assertEquals(
            listOf("Pneus", "Pneus/Hiver"),
            SavedItemStore.withAncestors(listOf("Pneus/Hiver")),
        )
    }

    @Test
    fun `a folder written two ways is drawn once`() {
        val cleaned = SavedItemStore.withAncestors(listOf("Pneus", "pneus/Hiver"))

        assertEquals(listOf("Pneus", "Pneus/Hiver"), cleaned)
    }

    @Test
    fun `a folder shows what is on it, not what is below it`() {
        assertEquals(listOf(summer), SavedItemStore.itemsIn("Pneus", all))
    }

    @Test
    fun `a folder asked deeply shows its sub-folders as well`() {
        assertEquals(
            listOf(tyre, summer),
            SavedItemStore.itemsIn("Pneus", all, deep = true),
        )
    }

    @Test
    fun `Sans dossier holds only the articles with no folder`() {
        assertEquals(listOf(loose), SavedItemStore.itemsIn("", all, deep = true))
    }

    @Test
    fun `a search inside a folder reaches its sub-folders`() {
        // The question inside "Pneus" is "is it here?", and a winter tyre filed
        // one level down is still in the shop's Pneus shelf.
        val scope = SavedItemStore.itemsIn("Pneus", all, deep = true)

        assertEquals(
            listOf(tyre),
            scope.filter { SavedItemStore.matches(it, "hiver") },
        )
    }

    @Test
    fun `deleting a folder moves its whole subtree up, not out`() {
        // deleteFolder is not pure, but this is the arithmetic it performs, and
        // the arithmetic is what would lose an article.
        val home = SavedItemStore.parentOf("Pneus/Hiver")
        val moved = all
            .filter { SavedItemStore.isIn(it.folder, "Pneus/Hiver") }
            .map { it.copy(folder = home) }

        assertEquals(listOf("Pneus"), moved.map { it.folder })
    }

    @Test
    fun `a folder named with a slash would silently become two levels`() {
        // The dialog refuses this name; the rule it relies on is the separator
        // being the only way a path grows a level.
        val path = SavedItemStore.join("Pneus", "Hiver/Clouté")

        assertEquals("Pneus/Hiver/Clouté", path)
        assertEquals("Pneus/Hiver", SavedItemStore.parentOf(path))
    }
}
