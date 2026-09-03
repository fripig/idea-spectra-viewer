package com.github.fripig.spectraviewer.model

import com.github.fripig.spectraviewer.model.ChangeOrderTest.Companion.change
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The candidate list is what the author filter offers. It is derived from the snapshot rather than
 * remembered, so an author the user can see on a row is always one they can select.
 */
class AuthorCandidatesTest {

    // ---- Requirement: Filter changes by author ----
    // The spec's "candidate lists by snapshot content" table, one case per row.

    private fun snapshotOf(vararg proposers: String?, group: ChangeGroup = ChangeGroup.ACTIVE) =
        SpectraSnapshot(
            active = proposers.mapIndexed { i, by -> change("change-$i", group = group, createdBy = by) },
            parked = emptyList(),
            archived = emptyList(),
            isSpectraProject = true,
        )

    @Test
    fun `two proposers yield two candidates`() {
        assertEquals(listOf("alice", "bob"), authorCandidates(snapshotOf("alice", "bob")).authors)
    }

    @Test
    fun `a repeated proposer appears once`() {
        assertEquals(listOf("alice", "bob"), authorCandidates(snapshotOf("bob", "alice", "bob")).authors)
    }

    @Test
    fun `candidates are ordered case-insensitively`() {
        assertEquals(
            listOf("alice", "Bob", "Carol"),
            authorCandidates(snapshotOf("Carol", "alice", "Bob")).authors,
        )
    }

    @Test
    fun `an unknown proposer is reported separately from the named ones`() {
        val candidates = authorCandidates(snapshotOf("alice", null))

        assertEquals(listOf("alice"), candidates.authors)
        assertTrue(candidates.hasUnknown, "the unknown candidate is offered when a change has no proposer")
    }

    @Test
    fun `a snapshot of unknown proposers alone offers the unknown candidate only`() {
        val candidates = authorCandidates(snapshotOf(null, null))

        assertEquals(emptyList<String>(), candidates.authors)
        assertTrue(candidates.hasUnknown)
    }

    @Test
    fun `an empty snapshot offers nothing`() {
        val candidates = authorCandidates(SpectraSnapshot.NOT_A_SPECTRA_PROJECT)

        assertEquals(emptyList<String>(), candidates.authors)
        assertFalse(candidates.hasUnknown)
    }

    @Test
    fun `the unknown candidate is absent when every change has a proposer`() {
        assertFalse(authorCandidates(snapshotOf("alice", "bob")).hasUnknown)
    }

    @Test
    fun `candidates are gathered from all three groups`() {
        val snapshot = SpectraSnapshot(
            active = listOf(change("a", createdBy = "alice")),
            parked = listOf(change("p", group = ChangeGroup.PARKED, createdBy = "bob")),
            archived = listOf(change("r", group = ChangeGroup.ARCHIVED, createdBy = "carol")),
            isSpectraProject = true,
        )

        assertEquals(
            listOf("alice", "bob", "carol"),
            authorCandidates(snapshot).authors,
            "an author whose only change is archived is still visible on a row, so it must be selectable",
        )
    }

    @Test
    fun `the unknown candidate is ordered after every named author`() {
        val candidates = authorCandidates(snapshotOf("Carol", "alice", "Bob", null))

        assertEquals(listOf("alice", "Bob", "Carol", "Unknown"), candidates.ordered)
    }

    @Test
    fun `display order holds the names alone when no proposer is unknown`() {
        assertEquals(listOf("alice", "bob"), authorCandidates(snapshotOf("alice", "bob")).ordered)
    }

    // A single candidate is nothing to choose between: selecting it filters out nothing, so the
    // control would be a button whose only effect is to look like it did something.

    @Test
    fun `nothing to choose between leaves the control unusable`() {
        assertFalse(AuthorCandidates.NONE.isUsable, "an empty list offers no operation at all")
        assertFalse(
            AuthorCandidates(authors = listOf("alice"), hasUnknown = false).isUsable,
            "selecting the only proposer hides nothing",
        )
        assertFalse(
            AuthorCandidates(authors = emptyList(), hasUnknown = true).isUsable,
            "selecting the only candidate hides nothing even when it is the unknown one",
        )
    }

    @Test
    fun `two candidates make the control usable`() {
        assertTrue(AuthorCandidates(authors = listOf("alice", "bob"), hasUnknown = false).isUsable)
        assertTrue(
            AuthorCandidates(authors = listOf("alice"), hasUnknown = true).isUsable,
            "a named author and the unknown candidate are two things to choose between",
        )
    }

    // ---- Requirement: Filter changes by author (reading a new snapshot) ----
    // Candidates and the surviving selection are produced together so that neither the order of
    // the two steps nor the list the selection is reconciled against can be got wrong at the call
    // site: reconciling against the previous snapshot's candidates would keep a vanished author
    // selected, and hide every change behind a control that no longer offers it.

    private fun proposedBy(vararg proposers: String?) =
        SpectraSnapshot(
            active = proposers.mapIndexed { i, by -> change("change-$i", createdBy = by) },
            parked = emptyList(),
            archived = emptyList(),
            isSpectraProject = true,
        )

    @Test
    fun `an author the new snapshot still holds keeps their selection`() {
        val state = reconcileAuthorFilter(proposedBy("alice", "bob"), ChangeFilter(authors = setOf("alice")))

        assertEquals(listOf("alice", "bob"), state.candidates.authors)
        assertEquals(setOf("alice"), state.filter.authors)
    }

    @Test
    fun `an author the new snapshot has lost is dropped from both the list and the selection`() {
        val state = reconcileAuthorFilter(proposedBy("alice"), ChangeFilter(authors = setOf("alice", "bob")))

        assertEquals(listOf("alice"), state.candidates.authors, "bob is no longer offered")
        assertEquals(setOf("alice"), state.filter.authors, "and so cannot stay selected")
    }

    @Test
    fun `the unknown selection is dropped once every change has a proposer`() {
        val state = reconcileAuthorFilter(
            proposedBy("alice", "bob"),
            ChangeFilter(text = "add", includeUnknownAuthor = true),
        )

        assertFalse(state.candidates.hasUnknown)
        assertFalse(state.filter.includeUnknownAuthor)
        assertEquals("add", state.filter.text, "the text box is not touched by a rescan")
    }
}
