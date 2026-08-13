package com.github.fripig.spectraviewer.model

import com.github.fripig.spectraviewer.toolwindow.GroupNode
import com.github.fripig.spectraviewer.toolwindow.GroupView
import com.github.fripig.spectraviewer.toolwindow.groupCountText
import com.github.fripig.spectraviewer.toolwindow.applyView
import com.github.fripig.spectraviewer.toolwindow.sortChanges
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

class ChangeOrderTest {

    @Test
    fun `the default order is Modified`() {
        assertEquals(ChangeOrder.MODIFIED, ChangeOrder.DEFAULT)
    }

    @Test
    fun `there are exactly three mutually exclusive orders`() {
        assertEquals(
            listOf(ChangeOrder.NAME, ChangeOrder.MODIFIED, ChangeOrder.CREATED),
            ChangeOrder.entries.toList(),
        )
    }

    @Test
    fun `a change carries a creation date and a modification date, either of which may be unknown`() {
        val dated = change("add-search", created = LocalDate.of(2026, 8, 10), modified = Instant.parse("2026-08-12T09:00:00Z"))
        assertEquals(LocalDate.of(2026, 8, 10), dated.created)
        assertEquals(Instant.parse("2026-08-12T09:00:00Z"), dated.modified)

        val undated = change("zebra-fix", created = null, modified = null)
        assertNull(undated.created, "an absent creation date is unknown, not a substitute value")
        assertNull(undated.modified, "an absent modification date is unknown, not a substitute value")
    }

    // ---- Requirement: Sort changes within groups ----
    // The three changes below are the spec's own example; the expected orders are its own too.

    private val addSearch = change("add-search", LocalDate.of(2026, 8, 10), Instant.parse("2026-08-12T09:00:00Z"))
    private val midTier = change("mid-tier", LocalDate.of(2026, 8, 12), Instant.parse("2026-08-11T17:00:00Z"))
    private val zebraFix = change("zebra-fix", created = null, modified = Instant.parse("2026-08-13T08:00:00Z"))
    private val unordered = listOf(zebraFix, addSearch, midTier)

    @Test
    fun `Name orders by name ascending`() {
        assertEquals(
            listOf("add-search", "mid-tier", "zebra-fix"),
            sortChanges(unordered, ChangeOrder.NAME).map { it.name },
        )
    }

    @Test
    fun `Modified orders by modification time, most recent first`() {
        assertEquals(
            listOf("zebra-fix", "add-search", "mid-tier"),
            sortChanges(unordered, ChangeOrder.MODIFIED).map { it.name },
        )
    }

    @Test
    fun `Created orders by creation date, most recent first, unknown last`() {
        assertEquals(
            listOf("mid-tier", "add-search", "zebra-fix"),
            sortChanges(unordered, ChangeOrder.CREATED).map { it.name },
        )
    }

    @Test
    fun `changes sharing a date fall back to name ascending`() {
        val sameDay = LocalDate.of(2026, 8, 12)
        val changes = listOf(change("zulu", sameDay), change("alpha", sameDay), change("mike", sameDay))

        assertEquals(
            listOf("alpha", "mike", "zulu"),
            sortChanges(changes, ChangeOrder.CREATED).map { it.name },
            "day-precision creation dates tie constantly, so the tiebreak must be stable",
        )
    }

    @Test
    fun `every dated change precedes every undated one`() {
        val changes = listOf(
            change("no-date-b", created = null, modified = null),
            change("dated-old", LocalDate.of(2026, 1, 1), Instant.parse("2026-01-01T00:00:00Z")),
            change("no-date-a", created = null, modified = null),
            change("dated-new", LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T00:00:00Z")),
        )

        listOf(ChangeOrder.CREATED, ChangeOrder.MODIFIED).forEach { order ->
            val sorted = sortChanges(changes, order).map { it.name }
            assertEquals(
                listOf("dated-new", "dated-old", "no-date-a", "no-date-b"),
                sorted,
                "$order must place unknown dates last, and even the oldest known date ahead of them",
            )
        }
    }

    @Test
    fun `sorting an empty list is not an error`() {
        ChangeOrder.entries.forEach { order ->
            assertEquals(emptyList<String>(), sortChanges(emptyList(), order).map { it.name })
        }
    }

    // ---- Requirement: Filter changes by name ----

    private fun snapshot(): SpectraSnapshot = SpectraSnapshot(
        active = listOf(addSearch, midTier, zebraFix),
        parked = listOf(change("search-cache", group = ChangeGroup.PARKED)),
        archived = emptyList(),
        isSpectraProject = true,
    )

    private fun view(filter: String, order: ChangeOrder = ChangeOrder.NAME) =
        applyView(snapshot(), order, filter).associateBy { it.group }

    @Test
    fun `an empty filter shows everything`() {
        val view = view("")

        assertEquals(listOf("add-search", "mid-tier", "zebra-fix"), view.names(ChangeGroup.ACTIVE))
        assertEquals(listOf("search-cache"), view.names(ChangeGroup.PARKED))
        assertEquals(3, view.getValue(ChangeGroup.ACTIVE).total, "total counts the unfiltered group")
    }

    @Test
    fun `filtering narrows every group at once`() {
        val view = view("search")

        assertEquals(listOf("add-search"), view.names(ChangeGroup.ACTIVE))
        assertEquals(listOf("search-cache"), view.names(ChangeGroup.PARKED))
    }

    @Test
    fun `filtering is case-insensitive`() {
        assertEquals(listOf("add-search"), view("SEARCH").names(ChangeGroup.ACTIVE))
    }

    @Test
    fun `a matching change keeps all of its artifacts`() {
        val withArtifacts = change("add-search").copy(artifacts = listOf("proposal.md", "tasks.md"))
        val snapshot = SpectraSnapshot(listOf(withArtifacts), emptyList(), emptyList(), true)

        val matched = applyView(snapshot, ChangeOrder.NAME, "search").first().changes.single()

        assertEquals(listOf("proposal.md", "tasks.md"), matched.artifacts)
    }

    @Test
    fun `an artifact path never makes its change match`() {
        val withArtifacts = change("mid-tier").copy(artifacts = listOf("proposal.md"))
        val snapshot = SpectraSnapshot(listOf(withArtifacts), emptyList(), emptyList(), true)

        assertEquals(
            emptyList<SpectraChange>(),
            applyView(snapshot, ChangeOrder.NAME, "proposal").first().changes,
            "the filter matches change names only",
        )
    }

    @Test
    fun `a filter matching nothing leaves all three groups present but empty`() {
        val view = view("zzz")

        assertEquals(ChangeGroup.entries.toSet(), view.keys, "every group survives an empty result")
        view.values.forEach { assertEquals(emptyList<SpectraChange>(), it.changes) }
        assertEquals(3, view.getValue(ChangeGroup.ACTIVE).total, "the total still reports what was filtered out")
    }

    @Test
    fun `the filtered result is still ordered`() {
        val view = applyView(snapshot(), ChangeOrder.MODIFIED, "e").associateBy { it.group }

        assertEquals(
            listOf("zebra-fix", "add-search", "mid-tier"),
            view.names(ChangeGroup.ACTIVE),
            "filtering must not discard the chosen order",
        )
    }

    // ---- Requirement: Display changes as a grouped tree (counts) ----

    @Test
    fun `an unfiltered group shows a single total`() {
        val node = GroupNode(applyView(snapshot(), ChangeOrder.NAME, "").first(), filtering = false)

        assertEquals("3", groupCountText(node))
    }

    @Test
    fun `a filtered group shows matched of total`() {
        val views = applyView(snapshot(), ChangeOrder.NAME, "search").associateBy { it.group }

        assertEquals("1/3", groupCountText(GroupNode(views.getValue(ChangeGroup.ACTIVE), filtering = true)))
        assertEquals("1/1", groupCountText(GroupNode(views.getValue(ChangeGroup.PARKED), filtering = true)))
        assertEquals("0/0", groupCountText(GroupNode(views.getValue(ChangeGroup.ARCHIVED), filtering = true)))
    }

    @Test
    fun `a group where everything matches still shows both numbers while filtering`() {
        val views = applyView(snapshot(), ChangeOrder.NAME, "e").associateBy { it.group }

        assertEquals(
            "3/3",
            groupCountText(GroupNode(views.getValue(ChangeGroup.ACTIVE), filtering = true)),
            "the second number is driven by the filter being active, not by the counts differing",
        )
    }

    private fun Map<ChangeGroup, GroupView>.names(group: ChangeGroup) =
        getValue(group).changes.map { it.name }

    companion object {
        /** Builds a change with only the fields a sort or filter test cares about. */
        fun change(
            name: String,
            created: LocalDate? = null,
            modified: Instant? = null,
            group: ChangeGroup = ChangeGroup.ACTIVE,
        ) = SpectraChange(
            name = name,
            group = group,
            directory = Path.of("/tmp", name),
            artifacts = emptyList(),
            progress = null,
            created = created,
            modified = modified,
        )
    }
}
