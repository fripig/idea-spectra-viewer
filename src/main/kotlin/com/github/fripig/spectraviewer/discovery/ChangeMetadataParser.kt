package com.github.fripig.spectraviewer.discovery

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The two fields the tool window needs out of a change's `.openspec.yaml`.
 *
 * The types are deliberately different: a creation date has day precision and comes from `created`,
 * a proposer is a display name and comes from `created_by`. Two same-typed nullable fields side by
 * side would be silently swappable at every construction site.
 */
data class ChangeMetadata(val created: LocalDate?, val createdBy: String?) {
    companion object {
        /** What an absent, unreadable, or unusable metadata file resolves to. */
        val UNKNOWN = ChangeMetadata(created = null, createdBy = null)
    }
}

/**
 * Reads [ChangeMetadata] out of a change's `.openspec.yaml`.
 *
 * Deliberately not a YAML parser. A whole YAML dependency and its surface would be carried for two
 * scalars; Spectra writes a flat key-value file, so a line scan reaches the same values.
 * Every deviation from what is expected — a missing file, a missing field, a value that is not an
 * ISO date, a name that is only an email address, a nested document — resolves to "unknown" rather
 * than an error, because a change must stay listed even when its metadata is unusable. One field
 * being unusable never costs the other.
 *
 * Both fields come out of a single pass, so displaying the proposer costs no extra file access.
 */
object ChangeMetadataParser {

    private const val CREATED_KEY = "created:"

    /** Distinct from [CREATED_KEY] by its colon: `created_by:` does not start with `created:`. */
    private const val CREATED_BY_KEY = "created_by:"

    /**
     * A metadata file is a handful of lines. Bounding the scan keeps a corrupt or absurdly large
     * file from being read in full just to discover it has none of the fields.
     */
    private const val MAX_LINES = 100

    /** The metadata carried by [text], with every unusable field reported as null. */
    fun parse(text: String): ChangeMetadata = parse(text.lineSequence())

    /** As [parse], reading from disk. Everything is unknown when the file is absent or unreadable. */
    fun parseFile(file: Path): ChangeMetadata = try {
        Files.newBufferedReader(file).use { reader ->
            parse(reader.lineSequence())
        }
    } catch (_: Exception) {
        ChangeMetadata.UNKNOWN
    }

    private fun parse(lines: Sequence<String>): ChangeMetadata {
        var created: LocalDate? = null
        var createdBy: String? = null
        var seenCreated = false
        var seenCreatedBy = false

        for (line in lines.take(MAX_LINES)) {
            when {
                // A top-level field starts at column zero; an indented key belongs to some other one.
                // Only the first occurrence of each field counts, so a duplicate further down the
                // file cannot overwrite what the document already stated.
                !seenCreated && line.startsWith(CREATED_KEY) -> {
                    seenCreated = true
                    created = parseDate(line.removePrefix(CREATED_KEY))
                }

                !seenCreatedBy && line.startsWith(CREATED_BY_KEY) -> {
                    seenCreatedBy = true
                    createdBy = parseDisplayName(line.removePrefix(CREATED_BY_KEY))
                }
            }
            if (seenCreated && seenCreatedBy) break
        }

        return ChangeMetadata(created = created, createdBy = createdBy)
    }

    private fun parseDate(value: String): LocalDate? = value
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /**
     * `created_by` carries `Name <email>`. Only the name is kept: a tree row has room for a name
     * beside the task counts, not for an address, and the address is not what the user typed to
     * identify themselves. A value that is nothing but an address therefore has no display name.
     */
    private fun parseDisplayName(value: String): String? = value
        .substringBefore('<')
        .trim()
        .takeIf { it.isNotEmpty() }
}
