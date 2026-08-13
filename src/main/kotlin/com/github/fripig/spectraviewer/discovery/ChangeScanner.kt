package com.github.fripig.spectraviewer.discovery

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.SpectraSnapshot
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Reads the Spectra layout straight off disk with [java.nio.file]. The CLI is not invoked (it need
 * not be on the PATH an IDE inherits) and the VFS is not used, because parked changes live inside
 * the git directory, which is excluded from the project index.
 *
 * The caller is responsible for running this off the EDT.
 */
object ChangeScanner {

    private val LOG = Logger.getInstance(ChangeScanner::class.java)

    /** The three source paths are pinned here so a future Spectra layout change has one landing spot. */
    private const val OPENSPEC_DIR = "openspec"
    private const val CHANGES_DIR = "changes"
    private const val ARCHIVE_DIR = "archive"
    private const val PARKED_ROOT_DIR = "spectra-app"
    private const val TASKS_FILE = "tasks.md"

    fun scan(
        projectRoot: Path,
        warn: (String, Throwable?) -> Unit = { message, t -> LOG.warn(message, t) },
    ): SpectraSnapshot {
        val openspecDir = projectRoot.resolve(OPENSPEC_DIR)
        if (!Files.isDirectory(openspecDir)) return SpectraSnapshot.NOT_A_SPECTRA_PROJECT

        val changesDir = openspecDir.resolve(CHANGES_DIR)
        val parkedDir = resolveGitDir(projectRoot)?.resolve(PARKED_ROOT_DIR)?.resolve(CHANGES_DIR)

        return SpectraSnapshot(
            active = scanGroup(changesDir, ChangeGroup.ACTIVE, warn) { it.name != ARCHIVE_DIR },
            parked = scanGroup(parkedDir, ChangeGroup.PARKED, warn),
            archived = scanGroup(changesDir.resolve(ARCHIVE_DIR), ChangeGroup.ARCHIVED, warn),
            isSpectraProject = true,
        )
    }

    private fun scanGroup(
        groupDir: Path?,
        group: ChangeGroup,
        warn: (String, Throwable?) -> Unit,
        accept: (Path) -> Boolean = { true },
    ): List<SpectraChange> {
        if (groupDir == null || !Files.isDirectory(groupDir)) return emptyList()

        val entries = try {
            Files.newDirectoryStream(groupDir).use { it.toList() }
        } catch (e: Exception) {
            warn("Spectra: cannot list $groupDir, ${group.displayName} group left empty", e)
            return emptyList()
        }

        return entries
            .filter { Files.isDirectory(it) && accept(it) }
            .mapNotNull { changeDir ->
                try {
                    readChange(changeDir, group)
                } catch (e: Exception) {
                    // One unreadable or vanished change must not cost the rest of the scan.
                    warn("Spectra: skipping unreadable change at $changeDir", e)
                    null
                }
            }
            .sortedBy { it.name }
    }

    private fun readChange(changeDir: Path, group: ChangeGroup): SpectraChange {
        val artifacts = Files.walk(changeDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.name.endsWith(".md", ignoreCase = true) }
                .map { relativeSlashPath(changeDir, it) }
                .toList()
        }.sorted()

        // `.openspec.yaml` is intentionally not read: nothing in the tree needs it, and parsing it
        // would create a way for a malformed metadata file to hide an otherwise usable change.
        return SpectraChange(
            name = changeDir.name,
            group = group,
            directory = changeDir.toAbsolutePath(),
            artifacts = artifacts,
            progress = TaskProgressParser.parseFile(changeDir.resolve(TASKS_FILE)),
        )
    }

    private fun relativeSlashPath(base: Path, file: Path): String =
        base.relativize(file).joinToString("/") { it.toString() }

    internal fun resolveGitDir(projectRoot: Path): Path? {
        val dotGit = projectRoot.resolve(".git")
        return try {
            when {
                Files.isDirectory(dotGit) -> dotGit
                Files.isRegularFile(dotGit) -> resolveGitDirPointer(projectRoot, dotGit)
                else -> null
            }
        } catch (_: Exception) {
            // An unresolvable git directory only means "no parked changes"; it is not a scan failure.
            null
        }
    }

    /**
     * A `.git` file marks a worktree or submodule: it points at the real git directory, which in a
     * worktree in turn points at the shared one via `commondir`. Parked changes live in the shared
     * directory, so both hops have to be followed.
     */
    private fun resolveGitDirPointer(projectRoot: Path, dotGitFile: Path): Path? {
        val pointer = String(Files.readAllBytes(dotGitFile), Charsets.UTF_8)
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("gitdir:") }
            ?.removePrefix("gitdir:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val gitDir = projectRoot.resolve(pointer).normalize()
        if (!Files.isDirectory(gitDir)) return null

        val commonDirFile = gitDir.resolve("commondir")
        if (!Files.isRegularFile(commonDirFile)) return gitDir

        val commonDirPointer = String(Files.readAllBytes(commonDirFile), Charsets.UTF_8)
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null

        val commonDir = gitDir.resolve(commonDirPointer).normalize()
        return commonDir.takeIf { Files.isDirectory(it) }
    }
}
