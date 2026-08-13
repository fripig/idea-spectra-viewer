package com.github.fripig.spectraviewer.model

/**
 * How changes are ordered within a group. Mirrors `spectra list --sort`, including its default, so
 * the terminal and the tool window present the same order for the same project.
 */
enum class ChangeOrder(val displayName: String) {
    NAME("Name"),
    MODIFIED("Modified"),
    CREATED("Created"),
    ;

    companion object {
        val DEFAULT = MODIFIED
    }
}
