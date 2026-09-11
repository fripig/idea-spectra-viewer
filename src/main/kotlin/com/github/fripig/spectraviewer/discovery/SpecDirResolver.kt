package com.github.fripig.spectraviewer.discovery

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Decides where a project keeps its Spectra changes, by reading `spec_dir` out of the project's
 * `.spectra.yaml` — the same rule the Spectra CLI applies. Current Spectra initialises new projects
 * under `docs/spectra` and writes that into the file; a file without the field, or no file at all,
 * means the layout of an older Spectra or of OpenSpec, which is `openspec`.
 *
 * The layout is never guessed from which directories exist on disk. A rule the CLI does not share
 * would let the tool window and the CLI disagree about a project, with no error to show for it.
 *
 * Deliberately not a YAML parser, for the same reason as [ChangeMetadataParser]: one scalar out of
 * a flat key-value file is reachable with a line scan.
 */
object SpecDirResolver {

    private const val CONFIG_FILE = ".spectra.yaml"
    private const val SPEC_DIR_KEY = "spec_dir:"
    private const val LEGACY_SPEC_DIR = "openspec"

    /**
     * A configuration file is a few dozen lines. Bounding the scan keeps a corrupt or absurdly large
     * file from being read in full just to discover it has no `spec_dir`.
     */
    private const val MAX_LINES = 200

    /**
     * The spec directory of [projectRoot]. Never fails: whatever cannot be used resolves to
     * `openspec`, and only a `spec_dir` that is present but unusable is reported through [warn] —
     * a missing field is the normal state of a legacy project, not a misconfiguration.
     */
    fun resolve(projectRoot: Path, warn: (String, Throwable?) -> Unit): Path {
        val legacy = projectRoot.resolve(LEGACY_SPEC_DIR)
        val value = readSpecDir(projectRoot.resolve(CONFIG_FILE)) ?: return legacy

        fun fallBack(reason: String, cause: Throwable? = null): Path {
            warn("Spectra: $CONFIG_FILE spec_dir \"$value\" $reason, falling back to $LEGACY_SPEC_DIR", cause)
            return legacy
        }

        if (value.isEmpty()) return fallBack("is empty")

        val relative = try {
            Path.of(value)
        } catch (e: InvalidPathException) {
            return fallBack("is not a valid path", e)
        }
        // `root != null` rather than `isAbsolute` alone: on Windows `/etc` has a root but no drive,
        // so it is not absolute, yet it still points away from the project.
        if (relative.isAbsolute || relative.root != null) return fallBack("is an absolute path")

        // Checked on absolute paths, because a relative project root normalises `..` segments away
        // against nothing and could no longer tell an escape from a subdirectory.
        val base = projectRoot.toAbsolutePath().normalize()
        if (!base.resolve(relative).normalize().startsWith(base)) {
            return fallBack("resolves outside the project root")
        }

        return projectRoot.resolve(relative).normalize()
    }

    /**
     * The normalised value of the first top-level `spec_dir`, or null when the file is absent,
     * unreadable, or carries no such key. Absence is the normal state of a legacy project, so it is
     * kept apart from a key whose value turns out to be unusable.
     */
    private fun readSpecDir(configFile: Path): String? = try {
        Files.newBufferedReader(configFile).use { reader ->
            reader.lineSequence()
                .take(MAX_LINES)
                // A top-level field starts at column zero; an indented key belongs to some other one.
                // The first occurrence wins, so a duplicate further down cannot overwrite it.
                .firstOrNull { it.startsWith(SPEC_DIR_KEY) }
                ?.let { normalise(it.removePrefix(SPEC_DIR_KEY)) }
        }
    } catch (_: Exception) {
        null
    }

    /** Strips a trailing comment and surrounding quotes, then trims. */
    private fun normalise(rawValue: String): String {
        val value = rawValue.trim()
        val quote = value.firstOrNull()
        if (quote == '"' || quote == '\'') {
            val closing = value.indexOf(quote, startIndex = 1)
            // An unterminated quote leaves nothing trustworthy to use.
            return if (closing < 0) "" else value.substring(1, closing).trim()
        }
        return stripComment(value).trim()
    }

    /** YAML starts a comment only at a `#` that opens the value or follows whitespace. */
    private fun stripComment(value: String): String {
        val commentAt = value.indices.firstOrNull { i ->
            value[i] == '#' && (i == 0 || value[i - 1].isWhitespace())
        }
        return if (commentAt == null) value else value.substring(0, commentAt)
    }
}
