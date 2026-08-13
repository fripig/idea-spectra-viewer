package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.discovery.ChangeScanner
import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.ChangeOrder
import com.github.fripig.spectraviewer.model.SpectraSnapshot
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SearchTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.event.MouseEvent
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpectraChangesPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

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
    private var filter = ""

    /**
     * Only the newest scan may touch the tree: a slow scan that lost a race with a later Refresh
     * would otherwise resurrect stale data.
     */
    private var latestRequest = 0
    private var disposed = false

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = SpectraTreeCellRenderer()
        tree.emptyText.setText(LOADING_TEXT)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openArtifactAt(event)
        }.installOn(tree)

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
        rebuildTree()

        if (snapshot.isSpectraProject) showView(treeView) else showMessage(EMPTY_STATE_TEXT)
    }

    /** Re-renders the current snapshot under the current order and filter. Touches no disk. */
    private fun rebuildTree() {
        val snapshot = lastSnapshot ?: return

        // The very first snapshot has no expansion state to preserve, so open Active for the user.
        val toExpand = if (loadedOnce) collectExpandedIds(tree) else setOf(ChangeGroup.ACTIVE.name)
        loadedOnce = true

        tree.model = buildTreeModel(applyView(snapshot, order, filter), filter.isNotEmpty())
        restoreExpandedIds(tree, toExpand)
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
        val actions = DefaultActionGroup(RefreshAction(), sortGroup)
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
                if (text == filter) return
                filter = text
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
        const val NOTIFICATION_GROUP_ID = "Spectra Viewer"
        // The tree always carries three group rows, so Tree.emptyText only ever shows before the
        // first snapshot lands — which is exactly the loading moment.
        const val LOADING_TEXT = "Loading Spectra changes…"
        const val EMPTY_STATE_TEXT = "This project is not initialised for Spectra."
        const val SCAN_FAILED_TEXT = "Scanning Spectra changes failed — see the IDE log for details."
        const val NO_PROJECT_DIR_TEXT = "Spectra could not determine this project's directory."
        const val FILTER_HINT_TEXT = "Filter by name"
    }
}
