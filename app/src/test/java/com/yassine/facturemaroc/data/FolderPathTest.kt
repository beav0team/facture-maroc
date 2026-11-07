package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a folder path behaves.
 *
 * Folders nest, and a folder is stored as one string — "Mobilier/Chaises" —
 * rather than as a node with an id and a parent. That keeps the file readable
 * and lets every catalogue written before nesting existed load unchanged, but it
 * moves the whole tree into string rules: what is one level up, what is below,
 * what is a sibling that merely starts with the same letters.
 *
 * Those rules decide which articles a folder shows, what a delete moves up, and
 * what a rename has to rewrite, so they are checked here rather than discovered
 * on the phone.
 */
class FolderPathTest {

    private val chair = SavedItem(label = "Chaise en rotin", price = 850.0, folder = "Mobilier/Chaises")
    private val table = SavedItem(label = "Table basse", price = 1200.0, folder = "Mobilier")
    private val cleaning = SavedItem(label = "Nettoyage", price = 300.0, folder = "Entretien")
    private val loose = SavedItem(label = "Montage", price = 50.0)
    private val all = listOf(chair, table, cleaning, loose)

    @Test
    fun `a path is split into its parent and its own name`() {
        assertEquals("Mobilier", SavedItemStore.parentOf("Mobilier/Chaises"))
        assertEquals("Chaises", SavedItemStore.nameOf("Mobilier/Chaises"))
    }

    @Test
    fun `a top-level folder has no parent and keeps its name`() {
        assertEquals("", SavedItemStore.parentOf("Mobilier"))
        assertEquals("Mobilier", SavedItemStore.nameOf("Mobilier"))
    }

    @Test
    fun `joining into the root is the name itself`() {
        assertEquals("Mobilier", SavedItemStore.join("", "Mobilier"))
        assertEquals("Mobilier/Chaises", SavedItemStore.join("Mobilier", "Chaises"))
    }

    @Test
    fun `a path is drawn with arrows, not slashes`() {
        assertEquals("Mobilier › Chaises", SavedItemStore.display("Mobilier/Chaises"))
        assertEquals("Mobilier", SavedItemStore.display("Mobilier"))
    }

    @Test
    fun `a folder is inside itself and inside everything above it`() {
        assertTrue(SavedItemStore.isIn("Mobilier/Chaises", "Mobilier"))
        assertTrue(SavedItemStore.isIn("Mobilier/Chaises/Rotin", "Mobilier"))
        assertTrue(SavedItemStore.isIn("Mobilier", "Mobilier"))
        assertTrue(SavedItemStore.isIn("mobilier/chaises", "Mobilier"))
    }

    @Test
    fun `a sibling that only starts with the same letters is not inside`() {
        // The trap a plain startsWith falls into: deleting "Mobilier" would take
        // "Mobilier d'appoint" with it, and the shop would lose a folder it
        // never named.
        assertFalse(SavedItemStore.isIn("Mobilier d'appoint", "Mobilier"))
        assertFalse(SavedItemStore.isIn("Entretien", "Mobilier"))
    }

    @Test
    fun `nothing is inside Sans dossier`() {
        // "" matches no prefix, so the unfiled shelf cannot swallow the tree.
        assertFalse(SavedItemStore.isIn("Mobilier", ""))
        assertTrue(SavedItemStore.isIn("", ""))
    }

    @Test
    fun `renaming rewrites the path and keeps everything below it`() {
        assertEquals("Mobilier/Tables", SavedItemStore.rebase("Mobilier", "Mobilier", "Mobilier/Tables"))
        assertEquals(
            "Mobilier/Tables/Chaises",
            SavedItemStore.rebase("Mobilier/Chaises", "Mobilier", "Mobilier/Tables"),
        )
    }

    @Test
    fun `renaming leaves the rest of the tree alone`() {
        assertEquals("Entretien", SavedItemStore.rebase("Entretien", "Mobilier", "Mobilier/Tables"))
    }

    @Test
    fun `only the direct children of a folder are listed`() {
        val folders = listOf("Mobilier", "Mobilier/Chaises", "Mobilier/Chaises/Rotin", "Entretien")

        assertEquals(
            listOf("Entretien", "Mobilier"),
            SavedItemStore.children("", folders),
        )
        assertEquals(
            listOf("Mobilier/Chaises"),
            SavedItemStore.children("Mobilier", folders),
        )
    }

    @Test
    fun `children are sorted by their own name, not by their stored path`() {
        val folders = listOf("Mobilier/Zeta", "Mobilier/alpha", "Mobilier/Beta")

        assertEquals(
            listOf("Mobilier/alpha", "Mobilier/Beta", "Mobilier/Zeta"),
            SavedItemStore.children("Mobilier", folders),
        )
    }

    @Test
    fun `a sub-folder always comes with the folder that holds it`() {
        // Otherwise the grid, which only draws children of the folder it stands
        // in, would hold a shelf nobody can reach.
        assertEquals(
            listOf("Mobilier", "Mobilier/Chaises"),
            SavedItemStore.withAncestors(listOf("Mobilier/Chaises")),
        )
    }

    @Test
    fun `a folder written two ways is drawn once`() {
        val cleaned = SavedItemStore.withAncestors(listOf("Mobilier", "mobilier/Chaises"))

        assertEquals(listOf("Mobilier", "Mobilier/Chaises"), cleaned)
    }

    @Test
    fun `a folder shows what is on it, not what is below it`() {
        assertEquals(listOf(table), SavedItemStore.itemsIn("Mobilier", all))
    }

    @Test
    fun `a folder asked deeply shows its sub-folders as well`() {
        assertEquals(
            listOf(chair, table),
            SavedItemStore.itemsIn("Mobilier", all, deep = true),
        )
    }

    @Test
    fun `Sans dossier holds only the articles with no folder`() {
        assertEquals(listOf(loose), SavedItemStore.itemsIn("", all, deep = true))
    }

    @Test
    fun `a search inside a folder reaches its sub-folders`() {
        // The question inside "Mobilier" is "is it here?", and a chair filed one
        // level down is still in the shop's Mobilier shelf.
        val scope = SavedItemStore.itemsIn("Mobilier", all, deep = true)

        assertEquals(
            listOf(chair),
            scope.filter { SavedItemStore.matches(it, "rotin") },
        )
    }

    @Test
    fun `deleting a folder moves its whole subtree up, not out`() {
        // deleteFolder is not pure, but this is the arithmetic it performs, and
        // the arithmetic is what would lose an article.
        val home = SavedItemStore.parentOf("Mobilier/Chaises")
        val moved = all
            .filter { SavedItemStore.isIn(it.folder, "Mobilier/Chaises") }
            .map { it.copy(folder = home) }

        assertEquals(listOf("Mobilier"), moved.map { it.folder })
    }

    @Test
    fun `a folder named with a slash would silently become two levels`() {
        // The dialog refuses this name; the rule it relies on is the separator
        // being the only way a path grows a level.
        val path = SavedItemStore.join("Mobilier", "Chaises/Rotin")

        assertEquals("Mobilier/Chaises/Rotin", path)
        assertEquals("Mobilier/Chaises", SavedItemStore.parentOf(path))
    }
}
