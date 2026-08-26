package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class CommandTextTest {

    // ---- Requirement: Send a Spectra command for the selected change ----

    @ParameterizedTest(name = "{0} on add-search produces {1}")
    @CsvSource(
        "APPLY,   /spectra-apply add-search",
        "INGEST,  /spectra-ingest add-search",
        "ARCHIVE, /spectra-archive add-search",
        "COMMIT,  /spectra-commit add-search",
    )
    fun `each command carries the change name after a single space`(command: SpectraCommand, expected: String) {
        assertEquals(expected, commandTextFor(command, "add-search"))
    }

    @Test
    fun `the command text is one line with no trailing newline`() {
        val text = commandTextFor(SpectraCommand.APPLY, "add-search")

        assertEquals(
            listOf(text),
            text.lines(),
            "a trailing newline would submit the command the moment it lands in the terminal",
        )
    }

    @Test
    fun `the four commands are offered in workflow order`() {
        assertEquals(
            listOf("/spectra-apply", "/spectra-ingest", "/spectra-archive", "/spectra-commit"),
            SpectraCommand.entries.map { it.slashCommand },
            "the submenu renders them in declaration order, so the enum fixes what the user sees",
        )
    }

    @Test
    fun `a single change node is the command target`() {
        val change = change("add-search", progress = TaskProgress(3, 7))

        assertEquals(
            change,
            commandTargetFor(listOf(ChangeNode(change))),
            "the target carries only the name; progress counts never reach the command text",
        )
    }

    @Test
    fun `two change nodes have no command target`() {
        val selection = listOf(ChangeNode(change("add-search")), ChangeNode(change("zebra-fix")))

        assertNull(
            commandTargetFor(selection),
            "the newline-joined semantics of copy would turn into two consecutive commands",
        )
    }

    @Test
    fun `a group node alone has no command target`() {
        assertNull(commandTargetFor(listOf(groupNode())))
    }

    @Test
    fun `an artifact node alone has no command target`() {
        assertNull(commandTargetFor(listOf(ArtifactNode(change("add-search"), "design.md"))))
    }

    @Test
    fun `an empty selection has no command target`() {
        assertNull(commandTargetFor(emptyList()))
    }

    @Test
    fun `group and artifact nodes do not block a single change target`() {
        val addSearch = change("add-search")

        val target = commandTargetFor(
            listOf(groupNode(), ChangeNode(addSearch), ArtifactNode(addSearch, "design.md")),
        )

        assertEquals(addSearch, target, "a rubber-band selection that caught neighbours still names one change")
    }

    /**
     * The name reaches the terminal verbatim — no quoting, no escaping. Nothing is executed on the
     * user's behalf, and the full command text is on screen both in the menu item and at the prompt
     * before the user presses Enter, so quoting would only disfigure ordinary names.
     */
    @Test
    fun `the change name is passed through verbatim`() {
        assertEquals("/spectra-apply weird name", commandTextFor(SpectraCommand.APPLY, "weird name"))
    }

    private companion object {
        fun change(
            name: String,
            group: ChangeGroup = ChangeGroup.ACTIVE,
            progress: TaskProgress? = null,
        ) = SpectraChange(
            name = name,
            group = group,
            directory = Path.of("/tmp", name),
            artifacts = emptyList(),
            progress = progress,
            created = null,
            modified = null,
            createdBy = null,
        )

        fun groupNode() =
            GroupNode(GroupView(ChangeGroup.ACTIVE, listOf(change("add-search")), total = 1), filtering = false)
    }
}
