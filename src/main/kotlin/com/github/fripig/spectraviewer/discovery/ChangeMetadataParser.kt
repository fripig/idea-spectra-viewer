package com.github.fripig.spectraviewer.discovery

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads the one field the tool window needs out of a change's `.openspec.yaml`: `created`.
 *
 * Deliberately not a YAML parser. A whole YAML dependency and its surface would be carried for a
 * single scalar; Spectra writes a flat key-value file, so a line scan reaches the same value.
 * Every deviation from what is expected — a missing file, a missing field, a value that is not an
 * ISO date, a nested document — resolves to "unknown" rather than an error, because a change must
 * stay listed even when its metadata is unusable.
 */
object ChangeMetadataParser {

    private const val CREATED_KEY = "created:"

    /**
     * A metadata file is a handful of lines. Bounding the scan keeps a corrupt or absurdly large
     * file from being read in full just to discover it has no `created` field.
     */
    private const val MAX_LINES = 100

    /** The date of the first top-level `created` field, or null when there is no usable one. */
    fun parseCreated(text: String): LocalDate? =
        parseCreated(text.lineSequence())

    /** As [parseCreated], reading from disk. Null when the file is absent or unreadable. */
    fun parseCreatedFile(file: Path): LocalDate? = try {
        Files.newBufferedReader(file).use { reader ->
            parseCreated(reader.lineSequence())
        }
    } catch (_: Exception) {
        null
    }

    private fun parseCreated(lines: Sequence<String>): LocalDate? = lines
        .take(MAX_LINES)
        // A top-level field starts at column zero; an indented `created:` belongs to some other key.
        .firstOrNull { it.startsWith(CREATED_KEY) }
        ?.removePrefix(CREATED_KEY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
}
