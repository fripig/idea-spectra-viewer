package com.github.fripig.spectraviewer.model

/**
 * What the tool window is filtering by, as one value rather than as a parameter per dimension.
 * "Is anything being filtered" and "does this change survive" each get a single answer here, so a
 * call site cannot apply one dimension and forget the other, or combine them differently than the
 * next call site does.
 *
 * [authors] holds proposer display names only. A change whose proposer is unknown is matched by
 * [includeUnknownAuthor] rather than by a null element in [authors]: a nullable element would turn
 * every membership test into a place to get the unknown case quietly wrong, and would hide from the
 * type that "unknown" is a choice the user makes.
 */
data class ChangeFilter(
    val text: String = "",
    val authors: Set<String> = emptySet(),
    val includeUnknownAuthor: Boolean = false,
) {
    /**
     * Whether this filter hides anything. Group rows switch from a single total to matched-of-total
     * on this, so it must answer for every dimension the filter has — not just the text box.
     */
    val isActive: Boolean get() = text.isNotEmpty()

    /** Whether [change] survives this filter. */
    fun matches(change: SpectraChange): Boolean = matchesText(change)

    /**
     * Empty text means "no text filter", not "a substring nothing contains": the latter would empty
     * the tree the moment the user cleared the search box.
     */
    private fun matchesText(change: SpectraChange): Boolean =
        text.isEmpty() || change.name.contains(text, ignoreCase = true)

    companion object {
        /** Filters nothing: every change survives and group rows show a plain total. */
        val NONE = ChangeFilter()
    }
}
