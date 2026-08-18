package com.github.fripig.spectraviewer.model

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * Where a change lives. Spectra keeps active and archived changes under `openspec/`, but moves
 * parked ones into the git directory, which is why they are invisible in the project tree.
 */
enum class ChangeGroup(val displayName: String) {
    ACTIVE("Active"),
    PARKED("Parked"),
    ARCHIVED("Archived"),
}

/** Status derived from task progress — Spectra's own database is deliberately not consulted. */
enum class ChangeStatus {
    DRAFT,
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
}

/** Checkbox counts parsed out of a change's `tasks.md`. */
data class TaskProgress(val complete: Int, val total: Int)

/**
 * One discovered change. [artifacts] holds every Markdown file below [directory], expressed as a
 * path relative to it and sorted as strings; [progress] is null when `tasks.md` is absent or holds
 * no countable checkbox.
 *
 * [created] and [modified] deliberately use different types: the creation date comes from
 * `.openspec.yaml` and has day precision, the modification time comes from the file system. Two
 * same-typed nullable date fields side by side would be silently swappable at every call site.
 *
 * [createdBy] is the proposer's display name, taken from the `created_by` field of the same
 * `.openspec.yaml` with the email address stripped; it is null when that field is absent or
 * carries no usable name.
 *
 * None of the three has a default value, so a new construction site cannot quietly report them
 * all as unknown.
 */
data class SpectraChange(
    val name: String,
    val group: ChangeGroup,
    val directory: Path,
    val artifacts: List<String>,
    val progress: TaskProgress?,
    val created: LocalDate?,
    val modified: Instant?,
    val createdBy: String?,
) {
    val status: ChangeStatus
        get() {
            val progress = progress ?: return ChangeStatus.DRAFT
            if (progress.total <= 0) return ChangeStatus.DRAFT
            return when (progress.complete) {
                0 -> ChangeStatus.NOT_STARTED
                progress.total -> ChangeStatus.COMPLETE
                else -> ChangeStatus.IN_PROGRESS
            }
        }
}

/**
 * Result of one scan. Rebuilt wholesale on every refresh so the tree can never show a mix of old
 * and new data.
 */
data class SpectraSnapshot(
    val active: List<SpectraChange>,
    val parked: List<SpectraChange>,
    val archived: List<SpectraChange>,
    val isSpectraProject: Boolean,
) {
    operator fun get(group: ChangeGroup): List<SpectraChange> = when (group) {
        ChangeGroup.ACTIVE -> active
        ChangeGroup.PARKED -> parked
        ChangeGroup.ARCHIVED -> archived
    }

    companion object {
        val NOT_A_SPECTRA_PROJECT = SpectraSnapshot(
            active = emptyList(),
            parked = emptyList(),
            archived = emptyList(),
            isSpectraProject = false,
        )
    }
}
