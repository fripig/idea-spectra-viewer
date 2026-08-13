package com.github.fripig.spectraviewer.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class ChangeMetadataParserTest {

    // The spec's "creation metadata cases" table, one case per method. Every row must leave the
    // change usable, so the only distinction here is date versus unknown.

    @Test
    fun `an ISO created field is parsed`() {
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parseCreated("created: 2026-08-10\n"))
    }

    @Test
    fun `a file without a created field yields unknown`() {
        assertNull(ChangeMetadataParser.parseCreated("schema: spec-driven\n"))
    }

    @Test
    fun `a created value that is not an ISO date yields unknown`() {
        assertNull(ChangeMetadataParser.parseCreated("created: last Tuesday\n"))
    }

    @Test
    fun `an empty created value yields unknown`() {
        assertNull(ChangeMetadataParser.parseCreated("created:\n"))
    }

    @Test
    fun `the created field is found among other fields`() {
        val text = """
            schema: spec-driven
            created: 2026-08-13
            created_by: fripig <fripig@gmail.com>
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 13), ChangeMetadataParser.parseCreated(text))
    }

    @Test
    fun `created_by is not mistaken for created`() {
        assertNull(
            ChangeMetadataParser.parseCreated("created_by: fripig <fripig@gmail.com>\n"),
            "a longer key that starts with the same letters is a different field",
        )
    }

    @Test
    fun `an indented created field is not top level`() {
        val text = """
            metadata:
              created: 2026-08-10
        """.trimIndent()
        assertNull(text.let(ChangeMetadataParser::parseCreated), "only a top-level field counts")
    }

    @Test
    fun `the first top-level created field wins`() {
        val text = """
            created: 2026-08-10
            created: 2026-08-11
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parseCreated(text))
    }

    @Test
    fun `an absent file yields unknown`(@TempDir dir: Path) {
        assertNull(ChangeMetadataParser.parseCreatedFile(dir.resolve(".openspec.yaml")))
    }

    @Test
    fun `a directory in place of the metadata file yields unknown`(@TempDir dir: Path) {
        val asDirectory = dir.resolve(".openspec.yaml")
        Files.createDirectory(asDirectory)
        assertNull(ChangeMetadataParser.parseCreatedFile(asDirectory), "unreadable must not throw")
    }

    @Test
    fun `a present file is read`(@TempDir dir: Path) {
        val file = dir.resolve(".openspec.yaml")
        Files.writeString(file, "schema: spec-driven\ncreated: 2026-08-10\n")
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parseCreatedFile(file))
    }
}
