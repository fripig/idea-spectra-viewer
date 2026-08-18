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
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parse("created: 2026-08-10\n").created)
    }

    @Test
    fun `a file without a created field yields unknown`() {
        assertNull(ChangeMetadataParser.parse("schema: spec-driven\n").created)
    }

    @Test
    fun `a created value that is not an ISO date yields unknown`() {
        assertNull(ChangeMetadataParser.parse("created: last Tuesday\n").created)
    }

    @Test
    fun `an empty created value yields unknown`() {
        assertNull(ChangeMetadataParser.parse("created:\n").created)
    }

    @Test
    fun `the created field is found among other fields`() {
        val text = """
            schema: spec-driven
            created: 2026-08-13
            created_by: fripig <fripig@gmail.com>
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 13), ChangeMetadataParser.parse(text).created)
    }

    @Test
    fun `created_by is not mistaken for created`() {
        assertNull(
            ChangeMetadataParser.parse("created_by: fripig <fripig@gmail.com>\n").created,
            "a longer key that starts with the same letters is a different field",
        )
    }

    @Test
    fun `an indented created field is not top level`() {
        val text = """
            metadata:
              created: 2026-08-10
        """.trimIndent()
        assertNull(ChangeMetadataParser.parse(text).created, "only a top-level field counts")
    }

    @Test
    fun `the first top-level created field wins`() {
        val text = """
            created: 2026-08-10
            created: 2026-08-11
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parse(text).created)
    }

    @Test
    fun `an absent file yields unknown`(@TempDir dir: Path) {
        assertNull(ChangeMetadataParser.parseFile(dir.resolve(".openspec.yaml")).created)
    }

    @Test
    fun `a directory in place of the metadata file yields unknown`(@TempDir dir: Path) {
        val asDirectory = dir.resolve(".openspec.yaml")
        Files.createDirectory(asDirectory)
        assertNull(ChangeMetadataParser.parseFile(asDirectory).created, "unreadable must not throw")
    }

    @Test
    fun `a present file is read`(@TempDir dir: Path) {
        val file = dir.resolve(".openspec.yaml")
        Files.writeString(file, "schema: spec-driven\ncreated: 2026-08-10\n")
        assertEquals(LocalDate.of(2026, 8, 10), ChangeMetadataParser.parseFile(file).created)
    }


    // The spec's "Creation date and proposer are reported together" and "One metadata field being
    // unusable does not affect the other" scenarios: one pass, two independent outcomes.

    @Test
    fun `both fields come out of one pass`() {
        val text = """
            schema: spec-driven
            created: 2026-08-10
            created_by: fripig <fripig@gmail.com>
        """.trimIndent()
        assertEquals(ChangeMetadata(LocalDate.of(2026, 8, 10), "fripig"), ChangeMetadataParser.parse(text))
    }

    @Test
    fun `an unusable created date leaves the proposer intact`() {
        val text = """
            created: last Tuesday
            created_by: fripig <fripig@gmail.com>
        """.trimIndent()
        assertEquals(ChangeMetadata(null, "fripig"), ChangeMetadataParser.parse(text))
    }

    @Test
    fun `an unusable proposer leaves the created date intact`() {
        val text = """
            created: 2026-08-10
            created_by: <fripig@gmail.com>
        """.trimIndent()
        assertEquals(ChangeMetadata(LocalDate.of(2026, 8, 10), null), ChangeMetadataParser.parse(text))
    }

    // The spec's "proposer display name cases" table. `created_by` carries a `Name <email>` string;
    // only the name is reported, and every unusable shape resolves to unknown rather than to a
    // placeholder, so a change is never listed with a proposer it does not have.

    @Test
    fun `a created_by name is taken from before the angle bracket`() {
        assertEquals("fripig", ChangeMetadataParser.parse("created_by: fripig <fripig@gmail.com>\n").createdBy)
    }

    @Test
    fun `a created_by name keeps its inner spaces`() {
        assertEquals(
            "Alice Chen",
            ChangeMetadataParser.parse("created_by: Alice Chen <a@example.com>\n").createdBy,
        )
    }

    @Test
    fun `a created_by value without an email is the whole name`() {
        assertEquals("fripig", ChangeMetadataParser.parse("created_by: fripig\n").createdBy)
    }

    @Test
    fun `a created_by value that is only an email yields unknown`() {
        assertNull(
            ChangeMetadataParser.parse("created_by: <fripig@gmail.com>\n").createdBy,
            "an address is not a name the user chose to display",
        )
    }

    @Test
    fun `an empty created_by value yields unknown`() {
        assertNull(ChangeMetadataParser.parse("created_by:\n").createdBy)
    }

    @Test
    fun `a file without a created_by field yields unknown`() {
        assertNull(ChangeMetadataParser.parse("created: 2026-08-10\n").createdBy)
    }

    @Test
    fun `an absent file yields an unknown proposer`(@TempDir dir: Path) {
        assertNull(ChangeMetadataParser.parseFile(dir.resolve(".openspec.yaml")).createdBy)
    }
}
