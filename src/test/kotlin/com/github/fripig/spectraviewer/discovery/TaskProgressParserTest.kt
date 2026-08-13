package com.github.fripig.spectraviewer.discovery

import com.github.fripig.spectraviewer.model.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path

class TaskProgressParserTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("checkboxTable")
    fun `each checkbox table row is counted as specified`(text: String, expected: TaskProgress?) {
        assertEquals(expected, TaskProgressParser.parse(text), "text was:\n$text")
    }

    @Test
    fun `the whole checkbox table counts five items of which two are complete`() {
        val document = """
            ## 2. Section heading

            - [ ] 1.1 Implement scanner
            - [x] 1.2 Write tests
            - [X] 1.3 Update docs
              - [ ] 1.4 Nested subtask
            - [ ] [P] 1.5 Parallel task
            - [~] 1.6 Unknown marker

            ```markdown
            - [ ] inside a fenced block
            ```
        """.trimIndent()

        assertEquals(TaskProgress(complete = 2, total = 5), TaskProgressParser.parse(document))
    }

    @Test
    fun `a fenced block only suppresses the lines it encloses`() {
        val document = """
            - [x] before the fence

            ```
            - [ ] inside the fence
            - [x] also inside the fence
            ```

            - [ ] after the fence
        """.trimIndent()

        assertEquals(TaskProgress(complete = 1, total = 2), TaskProgressParser.parse(document))
    }

    @Test
    fun `text without any checkbox has no progress`() {
        val document = """
            ## 1. Design

            Some prose that mentions [x] in passing but is not a list item.

            ## 2. Section heading
        """.trimIndent()

        assertNull(TaskProgressParser.parse(document))
    }

    @Test
    fun `empty text has no progress`() {
        assertNull(TaskProgressParser.parse(""))
    }

    @Test
    fun `parseFile reads counts from a tasks file`(@TempDir dir: Path) {
        val tasksFile = dir.resolve("tasks.md")
        Files.writeString(
            tasksFile,
            """
                - [x] 1.1 Done
                - [X] 1.2 Also done
                - [ ] 1.3 Pending
            """.trimIndent(),
        )

        assertEquals(TaskProgress(complete = 2, total = 3), TaskProgressParser.parseFile(tasksFile))
    }

    @Test
    fun `parseFile returns null when the tasks file is absent`(@TempDir dir: Path) {
        assertNull(TaskProgressParser.parseFile(dir.resolve("tasks.md")))
    }

    @Test
    fun `parseFile returns null when the tasks file holds no checkbox`(@TempDir dir: Path) {
        val tasksFile = dir.resolve("tasks.md")
        Files.writeString(tasksFile, "## 1. Implementation\n\nNothing to tick here.\n")

        assertNull(TaskProgressParser.parseFile(tasksFile))
    }

    companion object {
        /** One entry per row of the "checkbox parsing cases" table in the change-discovery spec. */
        @JvmStatic
        fun checkboxTable(): List<Arguments> = listOf(
            Arguments.of("- [ ] 1.1 Implement scanner", TaskProgress(complete = 0, total = 1)),
            Arguments.of("- [x] 1.2 Write tests", TaskProgress(complete = 1, total = 1)),
            Arguments.of("- [X] 1.3 Update docs", TaskProgress(complete = 1, total = 1)),
            Arguments.of("  - [ ] 1.4 Nested subtask", TaskProgress(complete = 0, total = 1)),
            Arguments.of("- [ ] [P] 1.5 Parallel task", TaskProgress(complete = 0, total = 1)),
            Arguments.of("- [~] 1.6 Unknown marker", null),
            Arguments.of("## 2. Section heading", null),
            Arguments.of("```\n- [ ] inside a fenced block\n```", null),
        )
    }
}
