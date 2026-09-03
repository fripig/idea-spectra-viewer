package com.github.fripig.spectraviewer.model

import com.github.fripig.spectraviewer.model.ChangeOrderTest.Companion.change
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The filter is one value, not a bag of parameters each call site has to remember to apply. These
 * tests pin the whole of it: what counts as filtering at all, and what one change matching means.
 */
class ChangeFilterTest {

    // ---- Requirement: Filter changes by name ----

    @Test
    fun `an empty filter is not filtering and matches everything`() {
        assertFalse(ChangeFilter.NONE.isActive, "an empty filter must not read as filtering")
        assertTrue(ChangeFilter.NONE.matches(change("add-dark-mode")))
    }

    @Test
    fun `filter text is matched as a substring of the name`() {
        val filter = ChangeFilter(text = "dark")

        assertTrue(filter.isActive)
        assertTrue(filter.matches(change("add-dark-mode")))
        assertFalse(filter.matches(change("fix-login")))
    }

    @Test
    fun `filter text is matched case-insensitively`() {
        assertTrue(ChangeFilter(text = "DARK").matches(change("add-dark-mode")))
        assertTrue(ChangeFilter(text = "dark").matches(change("ADD-DARK-MODE")))
    }

    // ---- Requirement: Filter changes by author ----

    private val byAlice = change("add-dark-mode", createdBy = "alice")
    private val byBob = change("fix-login", createdBy = "bob")
    private val byCarol = change("tidy-logs", createdBy = "carol")
    private val byNobody = change("legacy-change", createdBy = null)

    @Test
    fun `selecting no author leaves every change matching`() {
        val filter = ChangeFilter.NONE

        listOf(byAlice, byBob, byNobody).forEach {
            assertTrue(filter.matches(it), "${it.name} must survive when no author is selected")
        }
    }

    @Test
    fun `selecting one author keeps that author's changes alone`() {
        val filter = ChangeFilter(authors = setOf("alice"))

        assertTrue(filter.matches(byAlice))
        assertFalse(filter.matches(byBob))
        assertFalse(filter.matches(byNobody), "an unknown proposer is not the selected author")
    }

    @Test
    fun `selecting several authors matches any one of them`() {
        val filter = ChangeFilter(authors = setOf("alice", "bob"))

        assertTrue(filter.matches(byAlice))
        assertTrue(filter.matches(byBob))
        assertFalse(filter.matches(byCarol))
    }

    @Test
    fun `selecting the unknown candidate keeps changes with no proposer alone`() {
        val filter = ChangeFilter(includeUnknownAuthor = true)

        assertTrue(filter.matches(byNobody))
        assertFalse(filter.matches(byAlice))
    }

    @Test
    fun `the unknown candidate can be selected alongside named authors`() {
        val filter = ChangeFilter(authors = setOf("alice"), includeUnknownAuthor = true)

        assertTrue(filter.matches(byAlice))
        assertTrue(filter.matches(byNobody))
        assertFalse(filter.matches(byBob))
    }

    // The spec's "combinations of filter text and author selection" table, one case per row. The
    // three changes and the six rows are the spec's own; the point of the table is that the two
    // dimensions narrow each other rather than replacing one another.

    private val addLightMode = change("add-light-mode", createdBy = "bob")
    private val fixLoginByAlice = change("fix-login", createdBy = "alice")
    private val allThree = listOf(byAlice, addLightMode, fixLoginByAlice)

    private fun surviving(filter: ChangeFilter) = allThree.filter(filter::matches).map { it.name }

    @Test
    fun `no text and no author selection shows everything`() {
        assertEquals(listOf("add-dark-mode", "add-light-mode", "fix-login"), surviving(ChangeFilter.NONE))
    }

    @Test
    fun `no text with one author selected shows that author's changes`() {
        assertEquals(
            listOf("add-dark-mode", "fix-login"),
            surviving(ChangeFilter(authors = setOf("alice"))),
        )
    }

    @Test
    fun `text with no author selected shows every name match`() {
        assertEquals(listOf("add-dark-mode", "add-light-mode"), surviving(ChangeFilter(text = "add")))
    }

    @Test
    fun `text and one author narrow each other`() {
        assertEquals(
            listOf("add-dark-mode"),
            surviving(ChangeFilter(text = "add", authors = setOf("alice"))),
        )
    }

    @Test
    fun `text and two authors keep every name match proposed by either`() {
        assertEquals(
            listOf("add-dark-mode", "add-light-mode"),
            surviving(ChangeFilter(text = "add", authors = setOf("alice", "bob"))),
        )
    }

    @Test
    fun `text matching nothing leaves nothing however the authors are selected`() {
        assertEquals(
            emptyList<String>(),
            surviving(ChangeFilter(text = "zzz", authors = setOf("alice"))),
        )
    }

    @Test
    fun `an author selection alone counts as filtering`() {
        assertTrue(
            ChangeFilter(authors = setOf("alice")).isActive,
            "selecting an author hides changes, so the group rows must switch to matched-of-total",
        )
        assertTrue(ChangeFilter(includeUnknownAuthor = true).isActive)
    }

    // ---- Requirement: Filter changes by author (a refresh reconciles the selection) ----

    @Test
    fun `a refresh keeps the authors the new snapshot still offers`() {
        val filter = ChangeFilter(authors = setOf("alice", "bob"))

        val reconciled = filter.reconciledWith(AuthorCandidates(authors = listOf("alice"), hasUnknown = false))

        assertEquals(setOf("alice"), reconciled.authors, "an author who is gone cannot stay selected")
    }

    @Test
    fun `a refresh keeps every author that is still offered`() {
        val filter = ChangeFilter(authors = setOf("alice", "bob"))

        val reconciled = filter.reconciledWith(
            AuthorCandidates(authors = listOf("alice", "bob", "carol"), hasUnknown = false),
        )

        assertEquals(setOf("alice", "bob"), reconciled.authors, "a new candidate is not selected on the user's behalf")
    }

    @Test
    fun `a refresh drops the unknown candidate once every change has a proposer`() {
        val filter = ChangeFilter(authors = setOf("alice"), includeUnknownAuthor = true)

        val reconciled = filter.reconciledWith(AuthorCandidates(authors = listOf("alice"), hasUnknown = false))

        assertFalse(reconciled.includeUnknownAuthor)
        assertEquals(setOf("alice"), reconciled.authors)
    }

    @Test
    fun `a refresh leaves the filter text alone`() {
        val filter = ChangeFilter(text = "add", authors = setOf("bob"))

        assertEquals("add", filter.reconciledWith(AuthorCandidates.NONE).text)
    }

    @Test
    fun `a refresh that offers nothing clears the author selection entirely`() {
        val filter = ChangeFilter(authors = setOf("alice"), includeUnknownAuthor = true)

        assertEquals(ChangeFilter.NONE, filter.reconciledWith(AuthorCandidates.NONE))
    }
}
