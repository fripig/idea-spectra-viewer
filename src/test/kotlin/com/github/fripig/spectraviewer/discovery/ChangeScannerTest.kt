package com.github.fripig.spectraviewer.discovery

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.ChangeStatus
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.SpectraSnapshot
import com.github.fripig.spectraviewer.model.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.LocalDate

class ChangeScannerTest {

    // ---- Requirement: scan changes from all three Spectra sources ----

    @Test
    fun `mixed layout puts every change in the group matching its source`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        createChange(root.resolve("openspec/changes/archive/old-login"))
        createChange(root.resolve(".git/spectra-app/changes/dark-mode"))

        val warnings = mutableListOf<String>()
        val snapshot = ChangeScanner.scan(root) { message, _ -> warnings += message }

        assertEquals(listOf("add-search"), names(snapshot.active), "Active group")
        assertEquals(listOf("dark-mode"), names(snapshot.parked), "Parked group")
        assertEquals(listOf("old-login"), names(snapshot.archived), "Archived group")
        assertTrue(snapshot.isSpectraProject, "openspec/ exists, so this is a Spectra project")
        assertEquals(emptyList<String>(), warnings, "a fully readable layout must not warn")

        assertEquals(ChangeGroup.ACTIVE, snapshot.active.single().group)
        assertEquals(ChangeGroup.PARKED, snapshot.parked.single().group)
        assertEquals(ChangeGroup.ARCHIVED, snapshot.archived.single().group)
        assertSamePath(root.resolve("openspec/changes/add-search"), snapshot.active.single().directory)
        assertSamePath(root.resolve(".git/spectra-app/changes/dark-mode"), snapshot.parked.single().directory)
        assertSamePath(root.resolve("openspec/changes/archive/old-login"), snapshot.archived.single().directory)
    }

    @Test
    fun `the archive directory is never reported as an active change`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        createChange(root.resolve("openspec/changes/archive/old-login"))

        val snapshot = scan(root)

        assertEquals(listOf("add-search"), names(snapshot.active))
        assertTrue(
            snapshot.active.none { it.name == "archive" },
            "the archive container directory must not be an Active change",
        )
    }

    @Test
    fun `an absent source directory yields an empty group and leaves the others populated`(@TempDir root: Path) {
        // No openspec/changes/archive/ and no .git at all.
        createChange(root.resolve("openspec/changes/add-search"))

        val snapshot = scan(root)

        assertEquals(listOf("add-search"), names(snapshot.active))
        assertEquals(emptyList<String>(), names(snapshot.archived), "no archive directory")
        assertEquals(emptyList<String>(), names(snapshot.parked), "no git directory")
    }

    @Test
    fun `an absent changes directory leaves the project recognised as Spectra`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("openspec/specs"))
        createChange(root.resolve(".git/spectra-app/changes/dark-mode"))

        val snapshot = scan(root)

        assertTrue(snapshot.isSpectraProject)
        assertEquals(emptyList<String>(), names(snapshot.active))
        assertEquals(listOf("dark-mode"), names(snapshot.parked))
    }

    @Test
    fun `every discovered change is present regardless of order`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/zebra-fix"))
        createChange(root.resolve("openspec/changes/add-search"))
        createChange(root.resolve("openspec/changes/mid-tier"))

        val snapshot = scan(root)

        // Ordering is the presentation layer's decision, so the scan only promises membership.
        assertEquals(setOf("add-search", "mid-tier", "zebra-fix"), names(snapshot.active).toSet())
    }

    @Test
    fun `loose files next to change directories are not reported as changes`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        Files.writeString(root.resolve("openspec/changes/README.md"), "not a change\n")

        assertEquals(listOf("add-search"), names(scan(root).active))
    }

    // ---- Requirement: resolve the git directory including worktree indirection ----

    @Test
    fun `a real git directory is used as the git directory`(@TempDir root: Path) {
        val gitDir = root.resolve(".git")
        createChange(gitDir.resolve("spectra-app/changes/dark-mode"))
        Files.createDirectories(root.resolve("openspec/changes"))

        val resolved = ChangeScanner.resolveGitDir(root)
        assertNotNull(resolved, "a real .git directory must resolve")
        assertSamePath(gitDir, resolved!!)
        assertEquals(listOf("dark-mode"), names(scan(root).parked))
    }

    @Test
    fun `a git file pointing at a worktree resolves through commondir`(@TempDir root: Path) {
        // Layout of a `git worktree add`: the project root holds a .git *file*, the pointed-at
        // worktree directory holds a commondir file naming the shared git directory.
        val commonGitDir = root.resolve("main-checkout/.git")
        val worktreeGitDir = commonGitDir.resolve("worktrees/feature")
        Files.createDirectories(worktreeGitDir)
        Files.writeString(worktreeGitDir.resolve("commondir"), "../..\n")
        createChange(commonGitDir.resolve("spectra-app/changes/dark-mode"))

        val projectRoot = root.resolve("feature-checkout")
        Files.createDirectories(projectRoot)
        Files.writeString(projectRoot.resolve(".git"), "gitdir: ${worktreeGitDir.toAbsolutePath()}\n")
        createChange(projectRoot.resolve("openspec/changes/add-search"))

        val resolved = ChangeScanner.resolveGitDir(projectRoot)
        assertNotNull(resolved, "the commondir indirection must resolve to the shared git directory")
        assertSamePath(commonGitDir, resolved!!)

        val snapshot = scan(projectRoot)
        assertEquals(listOf("dark-mode"), names(snapshot.parked))
        assertEquals(listOf("add-search"), names(snapshot.active))
    }

    @Test
    fun `a missing git entry leaves Parked empty without failing the scan`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        createChange(root.resolve("openspec/changes/archive/old-login"))

        assertNull(ChangeScanner.resolveGitDir(root), "there is no .git entry to resolve")

        val snapshot = scan(root)
        assertEquals(emptyList<String>(), names(snapshot.parked))
        assertEquals(listOf("add-search"), names(snapshot.active))
        assertEquals(listOf("old-login"), names(snapshot.archived))
    }

    @Test
    fun `a git file pointing at a missing path leaves Parked empty`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        Files.writeString(root.resolve(".git"), "gitdir: ${root.resolve("gone/.git/worktrees/x")}\n")

        assertNull(ChangeScanner.resolveGitDir(root), "the gitdir pointer target does not exist")

        val snapshot = scan(root)
        assertEquals(emptyList<String>(), names(snapshot.parked))
        assertEquals(listOf("add-search"), names(snapshot.active))
    }

    // ---- Requirement: report per-change metadata ----

    @Test
    fun `artifacts list every markdown file relative to the change directory in string order`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/theme-work")
        Files.createDirectories(change.resolve("specs/theme-engine"))
        Files.writeString(change.resolve("proposal.md"), "# Proposal\n")
        Files.writeString(change.resolve("tasks.md"), "- [ ] 1.1 Do it\n")
        Files.writeString(change.resolve("specs/theme-engine/spec.md"), "## ADDED Requirements\n")
        Files.writeString(change.resolve(".openspec.yaml"), "schema: 1\n")

        val artifacts = scan(root).active.single().artifacts

        assertEquals(
            // Literal '/' on purpose: the contract promises slash-separated relative paths on
            // every platform, so the expectation must not follow the platform separator.
            listOf(
                "proposal.md",
                "specs/theme-engine/spec.md",
                "tasks.md",
            ),
            artifacts,
        )
    }

    @Test
    fun `a relative project root still yields absolute change directories`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"))
        val workingDir = Path.of("").toAbsolutePath()
        assumeTrue(workingDir.root == root.root, "relativizing needs both paths on the same file system root")

        val reported = scan(workingDir.relativize(root)).active.single()

        assertTrue(reported.directory.isAbsolute, "${reported.directory} must be absolute")
        assertEquals(root.resolve("openspec/changes/add-search").toRealPath(), reported.directory.toRealPath())
    }

    // ---- Requirement: Report per-change metadata (creation date) ----

    @Test
    fun `the creation date is read from the change metadata file`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/add-search")
        Files.createDirectories(change)
        Files.writeString(change.resolve(".openspec.yaml"), "schema: spec-driven\ncreated: 2026-08-10\n")

        assertEquals(LocalDate.of(2026, 8, 10), scan(root).active.single().created)
    }

    @Test
    fun `unusable creation metadata leaves the change listed with an unknown date`(@TempDir root: Path) {
        val cases = mapOf(
            "no-created" to "schema: spec-driven\n",
            "not-a-date" to "created: last Tuesday\n",
            "empty-value" to "created:\n",
        )
        cases.forEach { (name, yaml) ->
            val dir = root.resolve("openspec/changes/$name")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve(".openspec.yaml"), yaml)
        }
        val absent = root.resolve("openspec/changes/no-file")
        Files.createDirectories(absent)

        val active = scan(root).active.associateBy { it.name }

        assertEquals(
            setOf("no-created", "not-a-date", "empty-value", "no-file"),
            active.keys,
            "unusable metadata must never remove a change",
        )
        active.values.forEach { assertNull(it.created, "${it.name} should have an unknown creation date") }
    }

    // ---- Requirement: Report per-change metadata (proposer) ----

    @Test
    fun `the proposer is read from the change metadata file`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/add-search")
        Files.createDirectories(change)
        Files.writeString(
            change.resolve(".openspec.yaml"),
            "schema: spec-driven\ncreated: 2026-08-10\ncreated_by: fripig <fripig@gmail.com>\n",
        )

        val reported = scan(root).active.single()

        assertEquals("fripig", reported.createdBy, "the email address is not part of the display name")
        assertEquals(LocalDate.of(2026, 8, 10), reported.created, "the creation date is unaffected")
    }

    @Test
    fun `a change without a metadata file is reported with an unknown proposer`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/add-search")
        Files.createDirectories(change)
        Files.writeString(change.resolve("proposal.md"), "# Proposal\n")

        val reported = scan(root).active.single()

        assertEquals("add-search", reported.name, "a missing metadata file must never remove a change")
        assertNull(reported.createdBy)
    }

    // ---- Requirement: Report per-change metadata (modification date) ----

    @Test
    fun `the newest Markdown file supplies the modification date`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/theme-work")
        Files.createDirectories(change)
        val older = Instant.parse("2026-08-11T17:00:00Z")
        val newer = Instant.parse("2026-08-12T09:00:00Z")
        writeAt(change.resolve("proposal.md"), "# Proposal\n", older)
        writeAt(change.resolve("tasks.md"), "- [ ] 1.1 Something\n", newer)

        assertEquals(newer, scan(root).active.single().modified)
    }

    @Test
    fun `editing an artifact moves the modification date forward`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/theme-work")
        Files.createDirectories(change)
        writeAt(change.resolve("proposal.md"), "# Proposal\n", Instant.parse("2026-08-11T17:00:00Z"))
        val before = scan(root).active.single().modified

        // Only the file's content changes; the parent directory is untouched, which is exactly the
        // case a directory timestamp would miss.
        writeAt(change.resolve("proposal.md"), "# Proposal, revised\n", Instant.parse("2026-08-14T08:00:00Z"))
        val after = scan(root).active.single().modified

        assertNotNull(before)
        assertNotNull(after)
        assertTrue(after!! > before!!, "editing a Markdown file must move the modification date forward")
        assertEquals(Instant.parse("2026-08-14T08:00:00Z"), after)
    }

    @Test
    fun `a change with no Markdown files has no modification date`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/empty-change")
        Files.createDirectories(change)
        Files.writeString(change.resolve(".openspec.yaml"), "schema: spec-driven\n")

        val reported = scan(root).active.single()
        assertEquals("empty-change", reported.name)
        assertNull(reported.modified)
    }

    @Test
    fun `a change without openspec yaml is still reported`(@TempDir root: Path) {
        val change = root.resolve("openspec/changes/add-search")
        Files.createDirectories(change)
        Files.writeString(change.resolve("proposal.md"), "# Proposal\n")

        val reported = scan(root).active.single()

        assertEquals("add-search", reported.name)
        assertEquals(listOf("proposal.md"), reported.artifacts)
    }

    // ---- Requirement: derive change status from task progress ----

    @Test
    fun `status follows the progress derivation table`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/draft-change"), tasks = null)
        createChange(root.resolve("openspec/changes/fresh-change"), tasks = tasksMd(complete = 0, total = 8))
        createChange(root.resolve("openspec/changes/busy-change"), tasks = tasksMd(complete = 3, total = 8))
        createChange(root.resolve("openspec/changes/done-change"), tasks = tasksMd(complete = 8, total = 8))

        val byName = scan(root).active.associateBy { it.name }

        assertProgress(byName.getValue("draft-change"), null, ChangeStatus.DRAFT)
        assertProgress(byName.getValue("fresh-change"), TaskProgress(0, 8), ChangeStatus.NOT_STARTED)
        assertProgress(byName.getValue("busy-change"), TaskProgress(3, 8), ChangeStatus.IN_PROGRESS)
        assertProgress(byName.getValue("done-change"), TaskProgress(8, 8), ChangeStatus.COMPLETE)
    }

    @Test
    fun `a tasks file without any checkbox reports no progress`(@TempDir root: Path) {
        createChange(root.resolve("openspec/changes/add-search"), tasks = "## 1. Implementation\n\nProse only.\n")

        assertProgress(scan(root).active.single(), null, ChangeStatus.DRAFT)
    }

    // ---- Requirement: degrade gracefully on unreadable changes ----

    @Test
    fun `an unreadable change directory is skipped with a warning`(@TempDir root: Path) {
        val unreadable = root.resolve("openspec/changes/broken")
        createChange(root.resolve("openspec/changes/alpha"))
        createChange(unreadable)
        createChange(root.resolve("openspec/changes/gamma"))

        assumeTrue(
            Files.getFileStore(unreadable).supportsFileAttributeView(PosixFileAttributeView::class.java),
            "POSIX permissions are required to make a directory unreadable",
        )
        val original = Files.getPosixFilePermissions(unreadable)
        try {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"))
            assumeTrue(isUnreadable(unreadable), "test must not run as a user that bypasses POSIX permissions")

            val warnings = mutableListOf<String>()
            val snapshot = ChangeScanner.scan(root) { message, _ -> warnings += message }

            assertEquals(setOf("alpha", "gamma"), names(snapshot.active).toSet(), "readable changes survive")
            assertTrue(
                warnings.any { it.contains("broken") },
                "the warning must name the skipped change, got: $warnings",
            )
        } finally {
            Files.setPosixFilePermissions(unreadable, original)
        }
    }

    // ---- Requirement: recognise a non-Spectra project ----

    @Test
    fun `a project without an openspec directory is not a Spectra project`(@TempDir root: Path) {
        createChange(root.resolve(".git/spectra-app/changes/ghost"))

        val snapshot = scan(root)

        assertFalse(snapshot.isSpectraProject, "there is no openspec/ directory")
        // The shared model exposes NOT_A_SPECTRA_PROJECT for exactly this case: nothing is reported.
        assertEquals(SpectraSnapshot.NOT_A_SPECTRA_PROJECT.active, snapshot.active)
        assertEquals(SpectraSnapshot.NOT_A_SPECTRA_PROJECT.parked, snapshot.parked)
        assertEquals(SpectraSnapshot.NOT_A_SPECTRA_PROJECT.archived, snapshot.archived)
    }

    @Test
    fun `an openspec directory alone marks the project as Spectra`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("openspec"))

        assertTrue(scan(root).isSpectraProject)
    }

    // ---- helpers ----

    private fun scan(root: Path): SpectraSnapshot = ChangeScanner.scan(root) { _, _ -> }

    private fun names(changes: List<SpectraChange>): List<String> = changes.map { it.name }

    private fun assertProgress(change: SpectraChange, progress: TaskProgress?, status: ChangeStatus) {
        assertEquals(progress, change.progress, "progress of ${change.name}")
        assertEquals(status, change.status, "status of ${change.name}")
    }

    private fun assertSamePath(expected: Path, actual: Path) {
        // The contract says "absolute path"; toRealPath() alone would canonicalise that away and
        // still pass if the scanner ever handed back a relative path.
        assertTrue(actual.isAbsolute, "$actual must be absolute")
        assertEquals(expected.toRealPath(), actual.toRealPath())
    }

    private fun isUnreadable(dir: Path): Boolean = try {
        Files.newDirectoryStream(dir).use { it.iterator().hasNext() }
        false
    } catch (_: IOException) {
        true
    }

    /** Writes a file and pins its modification time, so date assertions do not depend on the clock. */
    private fun writeAt(file: Path, content: String, at: Instant) {
        Files.writeString(file, content)
        Files.setLastModifiedTime(file, FileTime.from(at))
    }

    private fun createChange(dir: Path, tasks: String? = "- [ ] 1.1 Something\n") {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(".openspec.yaml"), "schema: 1\ncreated: 2026-08-13\n")
        Files.writeString(dir.resolve("proposal.md"), "# ${dir.fileName}\n")
        if (tasks != null) Files.writeString(dir.resolve("tasks.md"), tasks)
    }

    private fun tasksMd(complete: Int, total: Int): String = buildString {
        append("## 1. Implementation\n\n")
        repeat(total) { index ->
            val mark = if (index < complete) "x" else " "
            append("- [$mark] 1.${index + 1} Task ${index + 1}\n")
        }
    }
}
