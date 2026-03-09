package com.claudecode.jetbrains.ui.commands

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Represents a file or directory entry in the mention picker.
 */
data class FileMentionEntry(
    val relativePath: String,
    val fileName: String,
    val icon: Icon?,
    val isDirectory: Boolean
)

/**
 * A popup that shows project files when the user types `@` in the input.
 * Supports fuzzy matching and displays file icons with relative paths.
 */
class FileMentionPicker(
    private val project: Project,
    private val textArea: JBTextArea,
    private val onFileSelected: (FileMentionEntry) -> Unit
) {

    private val listModel = CollectionListModel<FileMentionEntry>()
    private val list = JBList(listModel).apply {
        cellRenderer = FileMentionCellRenderer()
        visibleRowCount = 10
    }
    private var popup: JBPopup? = null
    private var cachedEntries: List<FileMentionEntry>? = null

    val isVisible: Boolean get() = popup?.isVisible == true

    fun show() {
        if (isVisible) return

        val entries = getOrLoadEntries()
        listModel.replaceAll(entries.take(MAX_DISPLAY_ITEMS))
        if (listModel.size > 0) {
            list.selectedIndex = 0
        }

        val scrollPane = JBScrollPane(list).apply {
            preferredSize = Dimension(400, 280)
            border = BorderFactory.createEmptyBorder()
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, list)
            .setRequestFocus(false)
            .setFocusable(false)
            .setResizable(false)
            .setMovable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(false)
            .createPopup()

        popup?.showUnderneathOf(textArea)
    }

    fun updateFilter(query: String) {
        val normalized = query.removePrefix("@").lowercase()
        val entries = getOrLoadEntries()

        val filtered = if (normalized.isEmpty()) {
            entries.take(MAX_DISPLAY_ITEMS)
        } else {
            fuzzyFilter(entries, normalized).take(MAX_DISPLAY_ITEMS)
        }

        listModel.replaceAll(filtered)
        if (listModel.size > 0) {
            list.selectedIndex = 0
        }
    }

    fun dismiss() {
        popup?.cancel()
        popup = null
    }

    fun selectCurrent() {
        val selected = list.selectedValue
        if (selected != null) {
            onFileSelected(selected)
        }
        dismiss()
    }

    fun moveUp() {
        val idx = list.selectedIndex
        if (idx > 0) {
            list.selectedIndex = idx - 1
            list.ensureIndexIsVisible(idx - 1)
        }
    }

    fun moveDown() {
        val idx = list.selectedIndex
        if (idx < listModel.size - 1) {
            list.selectedIndex = idx + 1
            list.ensureIndexIsVisible(idx + 1)
        }
    }

    fun invalidateCache() {
        cachedEntries = null
    }

    private fun getOrLoadEntries(): List<FileMentionEntry> {
        cachedEntries?.let { return it }

        val basePath = project.basePath ?: return emptyList()
        val baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return emptyList()
        val fileIndex = ProjectFileIndex.getInstance(project)
        val fileTypeManager = FileTypeManager.getInstance()

        val entries = mutableListOf<FileMentionEntry>()

        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (entries.size >= MAX_CACHED_ENTRIES) return false

                // Skip hidden directories and common excluded dirs
                if (file.isDirectory && file.name.startsWith(".")) return false
                if (file.isDirectory && file.name in EXCLUDED_DIRS) return false

                // Skip files in excluded content roots
                if (fileIndex.isExcluded(file)) return false

                val relativePath = VfsUtilCore.getRelativePath(file, baseDir) ?: return true

                val icon = if (file.isDirectory) {
                    com.intellij.icons.AllIcons.Nodes.Folder
                } else {
                    fileTypeManager.getFileTypeByFile(file).icon
                }

                entries.add(
                    FileMentionEntry(
                        relativePath = relativePath,
                        fileName = file.name,
                        icon = icon,
                        isDirectory = file.isDirectory
                    )
                )
                return true
            }
        })

        // Sort: directories first, then alphabetically
        entries.sortWith(compareBy({ !it.isDirectory }, { it.relativePath.lowercase() }))
        cachedEntries = entries
        return entries
    }

    /**
     * Fuzzy-filters entries by matching the query against both the file name
     * and the relative path. Entries are scored and sorted by relevance.
     */
    private fun fuzzyFilter(
        entries: List<FileMentionEntry>,
        query: String
    ): List<FileMentionEntry> {
        val trailingSlash = query.endsWith("/")
        val cleanQuery = query.trimEnd('/')

        return entries
            .mapNotNull { entry ->
                // If query ends with '/', only show directories and their contents
                if (trailingSlash && !entry.isDirectory
                    && !entry.relativePath.lowercase().startsWith("$cleanQuery/")
                ) {
                    return@mapNotNull null
                }

                val score = fuzzyScore(entry, cleanQuery) ?: return@mapNotNull null
                Pair(entry, score)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Computes a fuzzy match score. Returns null if no match.
     * Higher score = better match.
     */
    private fun fuzzyScore(entry: FileMentionEntry, query: String): Int? {
        val path = entry.relativePath.lowercase()
        val name = entry.fileName.lowercase()

        // Exact path prefix match — highest score
        if (path.startsWith(query)) return 1000 + (100 - path.length)

        // File name starts with query
        if (name.startsWith(query)) return 800 + (100 - name.length)

        // Path contains query as substring
        val pathIdx = path.indexOf(query)
        if (pathIdx >= 0) return 600 + (100 - pathIdx)

        // Fuzzy: all query chars appear in order within name
        if (fuzzyContains(name, query)) return 400 + (100 - name.length)

        // Fuzzy: all query chars appear in order within path
        if (fuzzyContains(path, query)) return 200 + (100 - path.length)

        return null
    }

    /**
     * Returns true if all characters in [query] appear in order within [text].
     */
    private fun fuzzyContains(text: String, query: String): Boolean {
        var qi = 0
        for (ch in text) {
            if (qi < query.length && ch == query[qi]) {
                qi++
            }
        }
        return qi == query.length
    }

    // ── Cell Renderer ───────────────────────────────────────────

    private class FileMentionCellRenderer : ListCellRenderer<FileMentionEntry> {

        override fun getListCellRendererComponent(
            list: JList<out FileMentionEntry>,
            value: FileMentionEntry?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout(6, 0)).apply {
                border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
            }

            // Icon
            val iconLabel = JLabel(value?.icon).apply {
                border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
            }

            // File name (bold) + relative path (grey)
            val nameLabel = JLabel(value?.fileName ?: "").apply {
                font = font.deriveFont(Font.BOLD)
            }

            val pathDisplay = value?.relativePath ?: ""
            val pathLabel = JLabel(pathDisplay).apply {
                foreground = if (isSelected) {
                    list.selectionForeground
                } else {
                    java.awt.Color.GRAY
                }
                font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            }

            val textPanel = JPanel(BorderLayout(4, 0)).apply {
                isOpaque = false
                add(nameLabel, BorderLayout.WEST)
                add(pathLabel, BorderLayout.CENTER)
            }

            panel.add(iconLabel, BorderLayout.WEST)
            panel.add(textPanel, BorderLayout.CENTER)

            if (isSelected) {
                panel.background = list.selectionBackground
                nameLabel.foreground = list.selectionForeground
            } else {
                panel.background = list.background
                nameLabel.foreground = list.foreground
            }

            panel.isOpaque = true
            return panel
        }
    }

    companion object {
        private const val MAX_DISPLAY_ITEMS = 50
        private const val MAX_CACHED_ENTRIES = 5000
        private val EXCLUDED_DIRS = setOf(
            "node_modules", "build", "out", "dist", ".gradle",
            ".idea", "__pycache__", "target", ".git"
        )
    }
}
