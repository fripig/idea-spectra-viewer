package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.discovery.ChangeScanner
import com.github.fripig.spectraviewer.terminal.TerminalCommandSender
import com.github.fripig.spectraviewer.model.AuthorCandidates
import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.ChangeFilter
import com.github.fripig.spectraviewer.model.ChangeOrder
import com.github.fripig.spectraviewer.model.SpectraSnapshot
import com.github.fripig.spectraviewer.model.reconcileAuthorFilter
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SearchTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpectraChangesPanel(private val project: Project) :
    SimpleToolWindowPanel(true, true), UiDataProvider, Disposable {

    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode()))
    private val treeView = JBScrollPane(tree)
    private val messageLabel = JBLabel()
    private val messageView = createMessageView(messageLabel)

    private var currentView: JComponent? = null
    private var scanning = false
    private var loadedOnce = false

    /**
     * The last snapshot is kept so that changing the order or the filter can rebuild the tree from
     * memory. Rescanning the disk to reorder rows would be pure latency.
     */
    private var lastSnapshot: SpectraSnapshot? = null
    private var order = ChangeOrder.DEFAULT
    private var filter = ChangeFilter.NONE

    /**
     * What the author filter offers, recomputed from each snapshot rather than accumulated, so a
     * proposer whose changes are gone stops being offered instead of lingering as a dead entry.
     */
    private var candidates = AuthorCandidates.NONE

    /**
     * Only the newest scan may touch the tree: a slow scan that lost a race with a later Refresh
     * would otherwise resurrect stale data.
     */
    private var latestRequest = 0
    private var disposed = false

    private val copyProvider = TreeCopyProvider()

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = SpectraTreeCellRenderer()
        tree.emptyText.setText(LOADING_TEXT)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openArtifactAt(event)
        }.installOn(tree)

        installCopyPopupMenu()

        setToolbar(createToolbar())
        showView(treeView)
        refresh()
    }

    fun refresh() {
        if (disposed || project.isDisposed) return

        val request = ++latestRequest
        scanning = true
        showView(treeView)
        tree.setPaintBusy(true)
        tree.emptyText.setText(LOADING_TEXT)

        val projectRoot = projectRoot()
        if (projectRoot == null) {
            applyOutcome(ScanOutcome.Failure(NO_PROJECT_DIR_TEXT), request)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val outcome = try {
                ScanOutcome.Success(ChangeScanner.scan(projectRoot))
            } catch (e: Exception) {
                LOG.warn("Spectra scan of $projectRoot failed", e)
                ScanOutcome.Failure(SCAN_FAILED_TEXT)
            }
            ApplicationManager.getApplication().invokeLater(
                { applyOutcome(outcome, request) },
                ModalityState.nonModal(),
            )
        }
    }

    private fun applyOutcome(outcome: ScanOutcome, request: Int) {
        if (disposed || project.isDisposed || request != latestRequest) return

        scanning = false
        tree.setPaintBusy(false)

        // A failed scan must not masquerade as "no Spectra here" — that would hide the problem.
        val snapshot = when (outcome) {
            is ScanOutcome.Failure -> return showMessage(outcome.message)
            is ScanOutcome.Success -> outcome.snapshot
        }

        lastSnapshot = snapshot
        // Candidates and the selection that survives them are decided together, so this cannot
        // reconcile against the previous snapshot's list or store a selection before reconciling it.
        val authors = reconcileAuthorFilter(snapshot, filter)
        candidates = authors.candidates
        filter = authors.filter
        rebuildTree()

        if (snapshot.isSpectraProject) showView(treeView) else showMessage(EMPTY_STATE_TEXT)
    }

    /** Re-renders the current snapshot under the current order and filter. Touches no disk. */
    private fun rebuildTree() {
        val snapshot = lastSnapshot ?: return

        // The very first snapshot has no expansion state to preserve, so open Active for the user.
        val toExpand = if (loadedOnce) collectExpandedIds(tree) else setOf(ChangeGroup.ACTIVE.name)
        loadedOnce = true

        tree.model = buildTreeModel(applyView(snapshot, order, filter), filter.isActive)
        restoreExpandedIds(tree, toExpand)
    }

    /**
     * The menu names what it copies — the tree has three kinds of row and two of them are not
     * copyable, so a generic "Copy" would leave the user to find that out by trying.
     *
     * Installed through the platform handler because that is what already knows how each OS asks
     * for a context menu; intercepting mouse events here would mean re-implementing that.
     */
    private fun installCopyPopupMenu() {
        val action = CopyChangeNameAction()
        ActionManager.getInstance().getAction(IdeActions.ACTION_COPY)?.let { action.copyShortcutFrom(it) }
        PopupHandler.installPopupMenu(tree, DefaultActionGroup(action, CommandGroup()), POPUP_PLACE)
    }

    /**
     * Whether a command can be sent to a terminal right now.
     *
     * The plugin check comes first and the short circuit is the point, not a micro-optimisation:
     * with the terminal plugin disabled, [TerminalCommandSender]'s method bodies would fail to
     * resolve their terminal types. Never reaching the call keeps them unresolved and the tool
     * window intact.
     *
     * The question asked is whether the terminal plugin's classes resolve, not whether the IDE
     * lists it as installed or enabled — resolvability is the condition that actually decides
     * whether the call below can run, and the plugin query APIs that answer the adjacent questions
     * are either deprecated or marked internal.
     */
    private fun hasTerminalTarget(): Boolean = terminalClassesResolvable && TerminalCommandSender.hasTarget(project)

    /**
     * Whether this plugin's class loader can reach the terminal plugin. An optional dependency puts
     * those classes within reach only while that plugin is running, so this answer is exactly the
     * one that matters.
     *
     * Cached because it cannot change without an IDE restart, and this is asked every time the
     * context menu opens.
     */
    private val terminalClassesResolvable: Boolean by lazy {
        try {
            // initialize = false: resolving the class is the whole question. Running its static
            // initialisers would be a side effect asked of a mere availability check.
            Class.forName(TERMINAL_MANAGER_CLASS, false, javaClass.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    /**
     * Publishing a [CopyProvider] is what makes the IDE's own Copy action work on this tree, so the
     * user's own keymap applies and nothing has to be discovered on screen first.
     */
    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[PlatformDataKeys.COPY_PROVIDER] = copyProvider
    }

    /**
     * Reads the selection straight off the tree — no scan, no rebuild, so copying cannot disturb the
     * expansion state or the filter.
     */
    private fun selectedCopyText(): String? = copyTextFor(selectedNodes())

    /** The selection as tree nodes. Reads the tree only — no scan, so nothing here can disturb it. */
    private fun selectedNodes(): List<SpectraNode> =
        tree.selectionPaths.orEmpty()
            .mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? SpectraNode }

    /**
     * Hands one command line to wherever it belongs, and is the only place that decides which.
     *
     * A second caller would be a second chance to forget the fallback, which is why [send][
     * TerminalCommandSender.send] returning false is handled here rather than reported outwards:
     * a command the user asked for reaches them either way.
     */
    private fun deliver(text: String) {
        if (sentToTerminal(text)) return
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    /**
     * Whether the text reached a terminal. Anything at all going wrong on that side answers no, and
     * the clipboard catches the command — the user asked for it and must end up holding it.
     *
     * Throwable rather than Exception because this call crosses into another plugin: with the
     * terminal plugin gone mid-session, resolving its classes raises an Error, not an Exception.
     * Cancellation is the one thing that must keep travelling, so it is rethrown first.
     */
    private fun sentToTerminal(text: String): Boolean =
        try {
            hasTerminalTarget() && TerminalCommandSender.send(project, text)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            LOG.warn("Sending a Spectra command to the terminal failed", e)
            false
        }

    private fun projectRoot(): Path? {
        val basePath = project.basePath ?: return null
        return try {
            Path.of(basePath)
        } catch (e: InvalidPathException) {
            LOG.warn("Project base path is not a valid file system path: $basePath", e)
            null
        }
    }

    private fun openArtifactAt(event: MouseEvent): Boolean {
        val path = tree.getPathForLocation(event.x, event.y) ?: return false
        val node = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ArtifactNode ?: return false
        openArtifact(node.file)
        return true
    }

    /** The VFS lookup refreshes from disk, so it stays off the EDT like the scan itself. */
    private fun openArtifact(file: Path) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
            ApplicationManager.getApplication().invokeLater(
                {
                    if (disposed || project.isDisposed) return@invokeLater
                    if (virtualFile == null || !virtualFile.isValid) {
                        notifyMissing(file)
                    } else {
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    }
                },
                ModalityState.nonModal(),
            )
        }
    }

    private fun notifyMissing(file: Path) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "File is no longer available",
                "$file no longer exists. Refresh the Spectra tool window.",
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun showMessage(text: String) {
        messageLabel.text = text
        showView(messageView)
    }

    private fun showView(view: JComponent) {
        if (currentView === view) return
        currentView = view
        setContent(view)
        revalidate()
        repaint()
    }

    private fun createToolbar(): JComponent {
        val sortGroup = DefaultActionGroup("Sort By", true).apply {
            templatePresentation.icon = AllIcons.ObjectBrowser.Sorted
            ChangeOrder.entries.forEach { add(SortAction(it)) }
        }
        val actions = DefaultActionGroup(RefreshAction(), sortGroup, AuthorFilterGroup())
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, actions, true)
        toolbar.targetComponent = this

        val bar = JBPanel<JBPanel<*>>(BorderLayout())
        bar.add(toolbar.component, BorderLayout.WEST)
        bar.add(createFilterField(), BorderLayout.CENTER)
        return bar
    }

    /** Typing re-renders from the snapshot in memory; the disk is not touched. */
    private fun createFilterField(): JComponent {
        val field = SearchTextField(false)
        field.textEditor.emptyText.setText(FILTER_HINT_TEXT)
        field.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val text = field.text
                if (text == filter.text) return
                filter = filter.copy(text = text)
                rebuildTree()
            }
        })
        return field
    }

    private fun createMessageView(label: JBLabel): JComponent {
        label.horizontalAlignment = SwingConstants.CENTER
        label.foreground = JBColor.GRAY
        val panel = JBPanel<JBPanel<*>>(GridBagLayout())
        panel.add(label)
        return panel
    }

    override fun dispose() {
        disposed = true
    }

    /**
     * The menu entry. It neither judges what is copyable nor touches the clipboard itself: both go
     * through [copyProvider], so the menu and the keyboard shortcut cannot drift apart.
     */
    private inner class CopyChangeNameAction : AnAction(COPY_ACTION_TEXT), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = copyProvider.performCopy(e.dataContext)

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = copyProvider.isCopyEnabled(e.dataContext)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /**
     * The command submenu. Its title states what invoking an item will actually do, so the user is
     * never told "Send to Terminal" by a menu that is about to write to the clipboard instead.
     *
     * Enabled only for a single change: the newline-joined text a multi-selection copy produces
     * would reach a shell as several consecutive commands.
     */
    private inner class CommandGroup : DefaultActionGroup(SEND_SUBMENU_TEXT, true), DumbAware {
        init {
            SpectraCommand.entries.forEach { add(CommandItem(it)) }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.text = if (hasTerminalTarget()) SEND_SUBMENU_TEXT else COPY_COMMAND_SUBMENU_TEXT
            e.presentation.isEnabled = commandTargetFor(selectedNodes()) != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /**
     * One command. Its label is the very line it delivers — the same string from the same function,
     * so what the user reads before clicking is what lands at the prompt afterwards.
     */
    private inner class CommandItem(private val command: SpectraCommand) : AnAction(), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            val target = commandTargetFor(selectedNodes()) ?: return
            deliver(commandTextFor(command, target.name))
        }

        override fun update(e: AnActionEvent) {
            val target = commandTargetFor(selectedNodes())
            e.presentation.isEnabled = target != null
            // Falls back to the bare command rather than a blank row: a disabled parent is not
            // expanded, but an item with no text at all would be a defect if it ever showed.
            e.presentation.text = target?.let { commandTextFor(command, it.name) } ?: command.slashCommand
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /**
     * Both answers come from [copyTextFor], so "Copy is enabled" and "Copy has something to write"
     * can never disagree — an enabled action that silently does nothing is the failure this avoids.
     */
    private inner class TreeCopyProvider : CopyProvider {
        override fun isCopyVisible(dataContext: DataContext): Boolean = true

        override fun isCopyEnabled(dataContext: DataContext): Boolean = selectedCopyText() != null

        override fun performCopy(dataContext: DataContext) {
            val text = selectedCopyText() ?: return
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /**
     * The author filter, as a popup of independently checkable entries rather than a combo box: the
     * tool window is narrow enough that a second field would crush the search box, and a checkable
     * action reports its own state instead of needing a component model kept in sync with the
     * selection as the candidate list changes underneath it.
     *
     * The children are rebuilt on every open because the candidate list follows the snapshot.
     */
    private inner class AuthorFilterGroup : ActionGroup(AUTHOR_FILTER_TEXT, true), DumbAware {
        init {
            templatePresentation.icon = AllIcons.General.User
            isPopup = true
        }

        override fun getChildren(e: AnActionEvent?): Array<AnAction> =
            (candidates.authors.map { NamedAuthorAction(it) } +
                if (candidates.hasUnknown) listOf(UnknownAuthorAction()) else emptyList())
                .toTypedArray()

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = candidates.isUsable
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /**
     * One proposer, checkable on its own: several may be checked at once and they widen the result
     * together. Carries the name rather than a menu label, so a proposer who happens to be called
     * "Unknown" toggles their own changes and not the unknown-proposer entry.
     */
    private inner class NamedAuthorAction(private val author: String) :
        ToggleAction(author), DumbAware {

        override fun isSelected(e: AnActionEvent): Boolean = author in filter.authors

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val authors = if (state) filter.authors + author else filter.authors - author
            filter = filter.copy(authors = authors)
            rebuildTree()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /** The changes with no proposer at all — offered only while the snapshot holds one. */
    private inner class UnknownAuthorAction :
        ToggleAction(AuthorCandidates.UNKNOWN_LABEL), DumbAware {

        override fun isSelected(e: AnActionEvent): Boolean = filter.includeUnknownAuthor

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            filter = filter.copy(includeUnknownAuthor = state)
            rebuildTree()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    /** Radio-style: the three orders are mutually exclusive and the active one carries the check. */
    private inner class SortAction(private val target: ChangeOrder) :
        ToggleAction(target.displayName), DumbAware {

        override fun isSelected(e: AnActionEvent): Boolean = order == target

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state || order == target) return
            order = target
            rebuildTree()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class RefreshAction : AnAction("Refresh", "Rescan Spectra changes", AllIcons.Actions.Refresh), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = refresh()

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !scanning
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private sealed interface ScanOutcome {
        @JvmInline
        value class Success(val snapshot: SpectraSnapshot) : ScanOutcome

        @JvmInline
        value class Failure(val message: String) : ScanOutcome
    }

    private companion object {
        val LOG = Logger.getInstance(SpectraChangesPanel::class.java)
        const val TOOLBAR_PLACE = "SpectraChangesToolWindow"
        const val POPUP_PLACE = "SpectraChangesToolWindowPopup"
        const val COPY_ACTION_TEXT = "Copy Change Name"
        const val SEND_SUBMENU_TEXT = "Send to Terminal"
        const val COPY_COMMAND_SUBMENU_TEXT = "Copy Command"
        const val TERMINAL_MANAGER_CLASS = "org.jetbrains.plugins.terminal.TerminalToolWindowManager"
        const val NOTIFICATION_GROUP_ID = "Spectra Viewer"
        // The tree always carries three group rows, so Tree.emptyText only ever shows before the
        // first snapshot lands — which is exactly the loading moment.
        const val LOADING_TEXT = "Loading Spectra changes…"
        const val EMPTY_STATE_TEXT = "This project is not initialised for Spectra."
        const val SCAN_FAILED_TEXT = "Scanning Spectra changes failed — see the IDE log for details."
        const val NO_PROJECT_DIR_TEXT = "Spectra could not determine this project's directory."
        const val FILTER_HINT_TEXT = "Filter by name"
        const val AUTHOR_FILTER_TEXT = "Filter by Author"
    }
}
