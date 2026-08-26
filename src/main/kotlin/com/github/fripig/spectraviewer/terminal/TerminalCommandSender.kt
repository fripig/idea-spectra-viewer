package com.github.fripig.spectraviewer.terminal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * The one place in this plugin that touches the terminal plugin. Delete this file and the tool
 * window keeps working — the submenu simply copies commands instead of sending them, which is also
 * what happens when the IDE has the terminal plugin disabled. The fallback is not a special case
 * bolted on: it is what this seam's absence already means.
 *
 * Every member takes and returns platform types only. Terminal types appear inside method bodies
 * and nowhere else, because loading a class resolves its signatures and supertypes but not the
 * types its method bodies mention. That is what lets a caller name this object without dragging
 * the terminal plugin in — see the guard in the panel.
 */
object TerminalCommandSender {

    /**
     * Whether a command can be written right now: the Terminal tool window has a selected tab that
     * one of the two engines recognises.
     *
     * The caller must have established that the terminal plugin is enabled before calling this.
     */
    fun hasTarget(project: Project): Boolean = writerFor(project) != null

    /**
     * Writes [text] at the selected tab's prompt without executing it, then brings that tab forward
     * with the focus.
     *
     * Returns false when there was nothing to write to — the tab can close between the menu opening
     * and the item being invoked. The caller falls back to the clipboard on false, so a command the
     * user asked for is never simply lost.
     */
    fun send(project: Project, text: String): Boolean {
        val writer = writerFor(project) ?: return false
        if (!writer(text)) return false

        // Focus has to follow the text: the user still owes this command an Enter, and an Enter
        // pressed on the tree would do nothing at all, which reads as "the command never arrived".
        //
        // Guarded separately from the write because the answer this function gives is "did the text
        // arrive". It did. A tab that refuses to come forward is worth a log line, not a second
        // delivery of the same command to the clipboard.
        try {
            TerminalToolWindowManager.getInstance(project).toolWindow?.activate(null, true)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Focusing the terminal after sending a Spectra command failed", e)
        }
        return true
    }

    /**
     * How to write to the selected tab, or null when nothing there can take text.
     *
     * Two engines have to be handled because the IDE ships both and the user can switch between
     * them. The reworked terminal is asked first — it is the default since 2025.2, and its tabs are
     * invisible to the classic manager: [TerminalToolWindowManager.findWidgetByContent] returns
     * null for them and its widget list comes back empty. Assuming one engine was the whole world
     * is exactly the bug this ordering exists to avoid.
     */
    private fun writerFor(project: Project): ((String) -> Boolean)? {
        val content = selectedContent(project) ?: return null
        return reworkedWriter(project, content) ?: classicWriter(content)
    }

    private fun selectedContent(project: Project): Content? =
        TerminalToolWindowManager.getInstance(project).toolWindow?.contentManager?.selectedContent

    /**
     * The reworked (default) engine. Its builder spells out that the text is not to be run:
     * `shouldExecute()` is what opts into running, so never calling it is the whole guarantee that
     * the user keeps the Enter.
     *
     * Preferred over writing raw bytes at the tty because the builder also handles bracketed paste
     * mode, which is what stops a program reading the prompt — a running Claude session, say — from
     * mistaking pasted text for typed keystrokes.
     */
    private fun reworkedWriter(project: Project, content: Content): ((String) -> Boolean)? {
        val tab = TerminalToolWindowTabsManager.getInstance(project).tabs.firstOrNull { it.content == content }
            ?: return null
        return { text -> tab.view.createSendTextBuilder().trySend(text) }
    }

    /** The classic engine, still reachable when the user switches the terminal back to it. */
    private fun classicWriter(content: Content): ((String) -> Boolean)? {
        val connector = TerminalToolWindowManager.findWidgetByContent(content)?.ttyConnector ?: return null
        return { text ->
            connector.write(text)
            true
        }
    }

    private val LOG = Logger.getInstance(TerminalCommandSender::class.java)
}
