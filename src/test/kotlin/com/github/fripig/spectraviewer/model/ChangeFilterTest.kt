package com.github.fripig.spectraviewer.model

import com.github.fripig.spectraviewer.model.ChangeOrderTest.Companion.change
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
}
