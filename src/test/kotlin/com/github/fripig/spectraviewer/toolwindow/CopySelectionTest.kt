package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CopySelectionTest {

    // ---- Requirement: Copy change names to the clipboard ----

    @Test
    fun `a single change node copies its name alone`() {
        val change = change("sort-and-filter-changes", progress = TaskProgress(3, 7))

        assertEquals(
            "sort-and-filter-changes",
            copyTextFor(listOf(ChangeNode(change))),
            "the copied text carries neither the group prefix nor the progress counts",
        )
    }

    @Test
    fun `a group node alone has nothing to copy`() {
        val group = GroupNode(GroupView(ChangeGroup.ACTIVE, listOf(change("add-search")), total = 1), filtering = false)

        assertNull(
            copyTextFor(listOf(group)),
            "null is what disables the action; an empty string would enable it and wipe the clipboard",
        )
    }

    @Test
    fun `an artifact node alone has nothing to copy`() {
        val artifact = ArtifactNode(change("add-search"), "design.md")

        assertNull(copyTextFor(listOf(artifact)))
    }

    @Test
    fun `an empty selection has nothing to copy`() {
        assertNull(copyTextFor(emptyList()))
    }

    @Test
    fun `several change nodes copy one name per line, in selection order`() {
        val selection = listOf(ChangeNode(change("add-search")), ChangeNode(change("zebra-fix")))

        assertEquals("add-search\nzebra-fix", copyTextFor(selection))
    }

    @Test
    fun `a mixed selection copies its change nodes and ignores the rest`() {
        val group = GroupNode(GroupView(ChangeGroup.ACTIVE, listOf(change("add-search")), total = 1), filtering = false)
        val addSearch = change("add-search")

        val text = copyTextFor(listOf(group, ChangeNode(addSearch), ArtifactNode(addSearch, "design.md")))

        assertEquals("add-search", text, "group and artifact nodes are skipped rather than blocking the copy")
    }

    companion object {
        /** Builds a change with only the fields a copy test cares about. */
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
        )
    }
}
