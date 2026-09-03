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
    val isActive: Boolean get() = text.isNotEmpty() || authors.isNotEmpty() || includeUnknownAuthor

    /** Whether [change] survives this filter. The two dimensions are combined as a conjunction. */
    fun matches(change: SpectraChange): Boolean = matchesText(change) && matchesAuthor(change)

    /**
     * Empty text means "no text filter", not "a substring nothing contains": the latter would empty
     * the tree the moment the user cleared the search box.
     */
    private fun matchesText(change: SpectraChange): Boolean =
        text.isEmpty() || change.name.contains(text, ignoreCase = true)

    /**
     * Selecting nothing means "no author filter", for the same reason empty text does. Selected
     * authors are compared exactly: they were picked from a list built out of these very strings,
     * so a loose comparison could only ever fold two candidates the user can tell apart.
     */
    private fun matchesAuthor(change: SpectraChange): Boolean {
        if (authors.isEmpty() && !includeUnknownAuthor) return true
        val proposer = change.createdBy ?: return includeUnknownAuthor
        return proposer in authors
    }

    /**
     * This filter as it applies to [candidates]: authors the new snapshot no longer offers lose
     * their selection, the rest keep it, and the text is untouched.
     *
     * Keeping a selection whose candidate has vanished would leave the user staring at an empty
     * tree with no way to undo it — the control that hid everything is no longer on screen. Nothing
     * is ever selected on the user's behalf: a new candidate arrives unselected.
     */
    fun reconciledWith(candidates: AuthorCandidates): ChangeFilter = copy(
        authors = authors intersect candidates.authors.toSet(),
        includeUnknownAuthor = includeUnknownAuthor && candidates.hasUnknown,
    )

    companion object {
        /** Filters nothing: every change survives and group rows show a plain total. */
        val NONE = ChangeFilter()
    }
}
