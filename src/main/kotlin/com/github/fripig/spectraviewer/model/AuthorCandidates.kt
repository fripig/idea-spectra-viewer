package com.github.fripig.spectraviewer.model

/**
 * What the author filter can offer, derived from a snapshot rather than remembered. Deriving it
 * means an author the user can read on a change row is always one they can select, and an author
 * whose changes are gone stops being offered instead of lingering as a dead entry.
 *
 * [hasUnknown] is kept apart from [authors] for the same reason [ChangeFilter] keeps
 * `includeUnknownAuthor` apart from its author set: "no proposer" is a real choice, not a name, and
 * folding it into the list of names would put a value in there that no change actually carries.
 */
data class AuthorCandidates(val authors: List<String>, val hasUnknown: Boolean) {

    /**
     * The candidates in display order. [UNKNOWN_LABEL] comes last rather than in its alphabetical
     * place: it is not a person, and sitting between two names would read as one.
     */
    val ordered: List<String> get() = if (hasUnknown) authors + UNKNOWN_LABEL else authors

    /**
     * Whether the author filter has anything to offer. One candidate is not a choice: selecting it
     * hides nothing, so the control would appear to act and then change nothing on screen.
     */
    val isUsable: Boolean get() = ordered.size >= 2

    companion object {
        const val UNKNOWN_LABEL = "Unknown"

        val NONE = AuthorCandidates(authors = emptyList(), hasUnknown = false)
    }
}

/**
 * Every proposer in [snapshot], across all three groups. Pure — no file system access — so it costs
 * nothing to recompute on each rebuild, and no stale copy can drift from the snapshot on screen.
 */
fun authorCandidates(snapshot: SpectraSnapshot): AuthorCandidates {
    val all = ChangeGroup.entries.flatMap { snapshot[it] }
    return AuthorCandidates(
        authors = all.mapNotNull { it.createdBy }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
        hasUnknown = all.any { it.createdBy == null },
    )
}
