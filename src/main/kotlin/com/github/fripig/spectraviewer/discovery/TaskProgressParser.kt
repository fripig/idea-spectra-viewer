package com.github.fripig.spectraviewer.discovery

import com.github.fripig.spectraviewer.model.TaskProgress
import java.nio.file.Files
import java.nio.file.Path

/**
 * Counts the Markdown checkboxes of a change's `tasks.md`. Spectra's own database is deliberately
 * not consulted, so the checked state of these lines is the only progress signal available.
 */
object TaskProgressParser {

    /**
     * Leading whitespace is stripped before matching. Only a single character between the brackets
     * is accepted so that markers Spectra does not define (`[~]`, `[]`) fall through uncounted
     * instead of being guessed at.
     */
    private val CHECKBOX = Regex("""(?:[-*+]|\d{1,9}[.)])[ \t]+\[(.)]""")

    fun parse(text: String): TaskProgress? {
        var complete = 0
        var total = 0
        var fenceMarker: Char? = null
        var fenceLength = 0

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimStart()
            val marker = line.firstOrNull()
            if (marker == '`' || marker == '~') {
                val run = line.takeWhile { it == marker }.length
                if (run >= 3) {
                    if (fenceMarker == null) {
                        fenceMarker = marker
                        fenceLength = run
                        continue
                    }
                    // A closing fence uses the same character, is at least as long, and carries no
                    // info string.
                    if (marker == fenceMarker && run >= fenceLength && line.drop(run).isBlank()) {
                        fenceMarker = null
                        continue
                    }
                }
            }
            if (fenceMarker != null) continue

            val state = CHECKBOX.matchAt(line, 0)?.groupValues?.get(1) ?: continue
            when (state) {
                " " -> total++
                "x", "X" -> {
                    total++
                    complete++
                }
            }
        }

        return if (total == 0) null else TaskProgress(complete = complete, total = total)
    }

    fun parseFile(tasksFile: Path): TaskProgress? {
        val bytes = try {
            if (!Files.isRegularFile(tasksFile)) return null
            Files.readAllBytes(tasksFile)
        } catch (_: Exception) {
            return null
        }
        // Decoding with the String constructor rather than readString: a stray malformed byte
        // should cost one replacement character, not the whole change's progress.
        return parse(String(bytes, Charsets.UTF_8))
    }
}
