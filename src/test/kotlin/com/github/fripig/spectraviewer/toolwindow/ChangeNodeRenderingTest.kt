package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.ChangeOrder
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ChangeNodeRenderingTest {

    // ---- Requirement: Show the proposer on change nodes ----

    // The spec's "node text by proposer and progress" table. The whole row is asserted, name
    // included, because the proposer's position between the name and the counts is the point: the
    // counts must stay at the end whatever the proposer's length.

    @Test
    fun `a proposer and progress both appear, in that order`() {
        assertEquals(
            "add-dark-mode  fripig  3/8",
            nodeText(change("add-dark-mode", createdBy = "fripig", progress = TaskProgress(3, 8))),
        )
    }

    @Test
    fun `an unknown proposer leaves no placeholder`() {
        assertEquals(
            "add-dark-mode  3/8",
            nodeText(change("add-dark-mode", createdBy = null, progress = TaskProgress(3, 8))),
        )
    }

    @Test
    fun `a proposer shows without progress`() {
        assertEquals(
            "add-dark-mode  fripig",
            nodeText(change("add-dark-mode", createdBy = "fripig", progress = null)),
        )
    }

    @Test
    fun `neither a proposer nor progress leaves the name alone`() {
        assertEquals(
            "add-dark-mode",
            nodeText(change("add-dark-mode", createdBy = null, progress = null)),
        )
    }

    @Test
    fun `every group renders the proposer the same way`() {
        ChangeGroup.entries.forEach { group ->
            assertEquals(
                "add-dark-mode  fripig  3/8",
                nodeText(change("add-dark-mode", createdBy = "fripig", progress = TaskProgress(3, 8), group = group)),
                "${group.displayName} must not render change nodes differently",
            )
        }
    }

    @Test
    fun `an archived change shows its proposer, not its archiver`() {
        // `archived_by` is never read, so the only name a node can show is the one that proposed it.
        val archived = change("old-login", createdBy = "alice", group = ChangeGroup.ARCHIVED)

        val text = nodeText(archived)

        assertTrue(text.contains("alice"), "the proposer is shown")
        assertFalse(text.contains("bob"), "no other name from the metadata file reaches the node")
    }

    // ---- The proposer stays out of sorting, filtering, copying, and node identity ----

    @Test
    fun `the filter does not match the proposer`() {
        val changes = listOf(change("add-dark-mode", createdBy = "fripig"))

        assertEquals(
            emptyList<SpectraChange>(),
            filterChanges(changes, "fripig"),
            "the filter matches change names only",
        )
        assertEquals(changes, filterChanges(changes, "dark"), "the name still matches")
    }

    @Test
    fun `copying a change with a proposer yields the name alone`() {
        val change = change("add-dark-mode", createdBy = "fripig", progress = TaskProgress(3, 8))

        assertEquals("add-dark-mode", copyTextFor(listOf(ChangeNode(change))))
    }

    @Test
    fun `the proposer does not enter a node id`() {
        val withProposer = change("add-dark-mode", createdBy = "fripig")
        val without = change("add-dark-mode", createdBy = null)

        assertEquals(
            ChangeNode(without).id,
            ChangeNode(withProposer).id,
            "an id that moved with the proposer would break expansion state after a metadata edit",
        )
    }

    @Test
    fun `the proposer does not affect name order`() {
        val zebra = change("zebra-fix", createdBy = "alice")
        val apple = change("apple-fix", createdBy = "zoe")

        assertEquals(
            listOf(apple, zebra),
            sortChanges(listOf(zebra, apple), ChangeOrder.NAME),
            "sorting reads the name, never the proposer",
        )
    }

    private companion object {

        /** The full text of a change node: the name, then every detail segment, in display order. */
        fun nodeText(change: SpectraChange): String =
            (listOf(change.name) + changeNodeDetails(change)).joinToString("  ")

        fun change(
            name: String,
            createdBy: String? = null,
            progress: TaskProgress? = null,
            group: ChangeGroup = ChangeGroup.ACTIVE,
        ) = SpectraChange(
            name = name,
            group = group,
            directory = Path.of("/tmp", name),
            artifacts = emptyList(),
            progress = progress,
            created = null,
            modified = null,
            createdBy = createdBy,
        )
    }
}
