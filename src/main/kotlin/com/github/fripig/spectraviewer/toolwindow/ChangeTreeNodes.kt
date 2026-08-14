package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.model.ChangeGroup
import com.github.fripig.spectraviewer.model.ChangeOrder
import com.github.fripig.spectraviewer.model.SpectraChange
import com.github.fripig.spectraviewer.model.SpectraSnapshot
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * One group as the tree should show it. [changes] is what survived the filter, already ordered;
 * [total] is how many the group holds before filtering, so the row can say "1 of 3" rather than
 * leaving the user to wonder whether the missing ones were filtered or never scanned.
 */
data class GroupView(val group: ChangeGroup, val changes: List<SpectraChange>, val total: Int)

/**
 * Turns a snapshot into what the tree shows. Pure — no file system access — so it is safe on the
 * EDT and a change of order or filter costs a rebuild rather than a rescan.
 *
 * An empty [filter] means "no filter": everything is shown. Treating it as a substring that nothing
 * contains would empty the tree the moment the user cleared the search box.
 */
fun applyView(snapshot: SpectraSnapshot, order: ChangeOrder, filter: String): List<GroupView> =
    ChangeGroup.entries.map { group ->
        val all = snapshot[group]
        GroupView(
            group = group,
            changes = sortChanges(filterChanges(all, filter), order),
            total = all.size,
        )
    }

/** Matches on the change name only; artifact paths never make their change match. */
fun filterChanges(changes: List<SpectraChange>, filter: String): List<SpectraChange> =
    if (filter.isEmpty()) changes else changes.filter { it.name.contains(filter, ignoreCase = true) }

/**
 * Orders changes for display. The scan leaves them unordered, so ordering costs no disk access and
 * the user can switch it without a rescan.
 *
 * A change whose date is unknown always sorts last, never first: treating unknown as the smallest
 * value would float an undated change to the top of a "most recent first" list, which reads as a
 * claim about its age rather than an absence of information.
 */
fun sortChanges(changes: List<SpectraChange>, order: ChangeOrder): List<SpectraChange> = when (order) {
    ChangeOrder.NAME -> changes.sortedBy { it.name }
    // Day-precision creation dates tie constantly, and file times can tie too, so both date orders
    // need the name as a tiebreak to stay stable between rebuilds.
    ChangeOrder.MODIFIED -> changes.sortedWith(mostRecentFirst(SpectraChange::modified))
    ChangeOrder.CREATED -> changes.sortedWith(mostRecentFirst(SpectraChange::created))
}

private fun <T : Comparable<T>> mostRecentFirst(date: (SpectraChange) -> T?): Comparator<SpectraChange> =
    compareBy<SpectraChange> { date(it) == null }
        .thenByDescending(nullsLast()) { date(it) }
        .thenBy { it.name }

/**
 * User object of every tree row. [id] is stable across rebuilds, which is what lets Refresh restore
 * the expansion state even though each snapshot replaces the whole model.
 */
sealed interface SpectraNode {
    val id: String
}

/**
 * Holds the [GroupView] rather than loose matched and total counts: two adjacent Int parameters
 * would compile just as happily swapped.
 */
data class GroupNode(val view: GroupView, val filtering: Boolean) : SpectraNode {
    override val id: String get() = view.group.name
}

/**
 * The count shown next to a group name: a single total normally, matched-of-total while filtering.
 * Without the total, a group dropping from 20 to 0 reads as lost data rather than a narrow search.
 */
fun groupCountText(node: GroupNode): String =
    if (node.filtering) "${node.view.changes.size}/${node.view.total}" else "${node.view.total}"

data class ChangeNode(val change: SpectraChange) : SpectraNode {
    override val id: String get() = "${change.group.name}/${change.name}"
}

data class ArtifactNode(val change: SpectraChange, val relativePath: String) : SpectraNode {
    override val id: String get() = "${change.group.name}/${change.name}/$relativePath"

    val file: Path get() = change.directory.resolve(relativePath)
}

/**
 * What the Copy action puts on the clipboard for [selection], or null when it holds no change node.
 * Pure, so the whole rule is testable without the IDE, and the provider decides "is Copy enabled"
 * from the same null — one judgement, so an action can never look enabled and then do nothing.
 *
 * Group and artifact nodes are skipped rather than blocking the copy: a rubber-band selection that
 * happened to catch a group row should still yield the changes the user was after.
 */
fun copyTextFor(selection: List<SpectraNode>): String? = selection
    .filterIsInstance<ChangeNode>()
    .takeIf { it.isNotEmpty() }
    ?.joinToString("\n") { it.change.name }

/**
 * Builds the whole tree from one snapshot. The three group nodes are always present, including the
 * empty ones, so the user can tell "no parked changes" apart from "parked changes not scanned".
 */
fun buildTreeModel(views: List<GroupView>, filtering: Boolean): DefaultTreeModel {
    val root = DefaultMutableTreeNode()
    for (view in views) {
        val changes = view.changes
        val groupNode = DefaultMutableTreeNode(GroupNode(view, filtering))
        for (change in changes) {
            val changeNode = DefaultMutableTreeNode(ChangeNode(change))
            for (artifact in change.artifacts) {
                changeNode.add(DefaultMutableTreeNode(ArtifactNode(change, artifact), false))
            }
            groupNode.add(changeNode)
        }
        root.add(groupNode)
    }
    return DefaultTreeModel(root)
}

fun collectExpandedIds(tree: JTree): Set<String> {
    val root = tree.model?.root as? DefaultMutableTreeNode ?: return emptySet()
    val ids = LinkedHashSet<String>()
    collectExpandedIds(tree, root, ids)
    return ids
}

private fun collectExpandedIds(tree: JTree, parent: DefaultMutableTreeNode, ids: MutableSet<String>) {
    for (index in 0 until parent.childCount) {
        val child = parent.getChildAt(index) as? DefaultMutableTreeNode ?: continue
        val node = child.userObject as? SpectraNode ?: continue
        if (!tree.isExpanded(TreePath(child.path))) continue
        ids.add(node.id)
        collectExpandedIds(tree, child, ids)
    }
}

fun restoreExpandedIds(tree: JTree, ids: Set<String>) {
    if (ids.isEmpty()) return
    val root = tree.model?.root as? DefaultMutableTreeNode ?: return
    restoreExpandedIds(tree, root, ids)
}

private fun restoreExpandedIds(tree: JTree, parent: DefaultMutableTreeNode, ids: Set<String>) {
    for (index in 0 until parent.childCount) {
        val child = parent.getChildAt(index) as? DefaultMutableTreeNode ?: continue
        val node = child.userObject as? SpectraNode ?: continue
        if (child.isLeaf || node.id !in ids) continue
        tree.expandPath(TreePath(child.path))
        restoreExpandedIds(tree, child, ids)
    }
}

class SpectraTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
            is GroupNode -> {
                icon = AllIcons.Nodes.Folder
                append(node.view.group.displayName)
                append("  ${groupCountText(node)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is ChangeNode -> {
                icon = AllIcons.Nodes.Module
                append(node.change.name)
                val progress = node.change.progress
                if (progress != null) {
                    append("  ${progress.complete}/${progress.total}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }

            is ArtifactNode -> {
                icon = AllIcons.FileTypes.Markdown
                append(node.relativePath)
            }
        }
    }
}
