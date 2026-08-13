package com.github.fripig.spectraviewer.toolwindow

import com.github.fripig.spectraviewer.model.ChangeGroup
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
 * User object of every tree row. [id] is stable across rebuilds, which is what lets Refresh restore
 * the expansion state even though each snapshot replaces the whole model.
 */
sealed interface SpectraNode {
    val id: String
}

data class GroupNode(val group: ChangeGroup, val count: Int) : SpectraNode {
    override val id: String get() = group.name
}

data class ChangeNode(val change: SpectraChange) : SpectraNode {
    override val id: String get() = "${change.group.name}/${change.name}"
}

data class ArtifactNode(val change: SpectraChange, val relativePath: String) : SpectraNode {
    override val id: String get() = "${change.group.name}/${change.name}/$relativePath"

    val file: Path get() = change.directory.resolve(relativePath)
}

/**
 * Builds the whole tree from one snapshot. The three group nodes are always present, including the
 * empty ones, so the user can tell "no parked changes" apart from "parked changes not scanned".
 */
fun buildTreeModel(snapshot: SpectraSnapshot): DefaultTreeModel {
    val root = DefaultMutableTreeNode()
    for (group in ChangeGroup.entries) {
        val changes = snapshot[group]
        val groupNode = DefaultMutableTreeNode(GroupNode(group, changes.size))
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
                append(node.group.displayName)
                append("  ${node.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
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
