package com.claudecode.jetbrains.ui.sessions

import com.claudecode.jetbrains.cli.SessionInfo
import com.claudecode.jetbrains.cli.SessionManager
import com.claudecode.jetbrains.services.ClaudeState
import com.claudecode.jetbrains.services.ClaudeStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A popup panel that displays session history grouped by time period.
 * Supports search, rename, delete, resume, and new-conversation actions.
 */
class SessionHistoryPanel(
    private val project: Project,
    private val onNewConversation: () -> Unit,
    private val onResumeSession: (SessionInfo) -> Unit
) {

    private var popup: JBPopup? = null

    fun show(owner: Component) {
        val panel = buildPanel()
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle("Past Conversations")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setMinSize(Dimension(340, 200))
            .createPopup()

        popup?.showUnderneathOf(owner)
    }

    private fun buildPanel(): JPanel {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(400, 450)

        // ── Top: New Conversation button + Search ────────────────
        val topPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        val newConvButton = JButton("New Conversation").apply {
            icon = AllIcons.General.Add
            addActionListener {
                popup?.cancel()
                onNewConversation()
            }
        }
        topPanel.add(newConvButton, BorderLayout.NORTH)

        val searchField = SearchTextField(false)
        searchField.preferredSize = Dimension(380, 30)
        val searchWrapper = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 0, 4, 0)
            add(searchField, BorderLayout.CENTER)
        }
        topPanel.add(searchWrapper, BorderLayout.SOUTH)

        root.add(topPanel, BorderLayout.NORTH)

        // ── Center: Session list ──────────────────────────────
        val listPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 8, 8, 8)
        }

        val scrollPane = JBScrollPane(listPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy =
                JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        root.add(scrollPane, BorderLayout.CENTER)

        // Populate initially
        populateList(listPanel, "")

        // Wire search
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) =
                onSearch(searchField, listPanel)
            override fun removeUpdate(e: DocumentEvent?) =
                onSearch(searchField, listPanel)
            override fun changedUpdate(e: DocumentEvent?) =
                onSearch(searchField, listPanel)
        })

        return root
    }

    private fun onSearch(searchField: SearchTextField, listPanel: JPanel) {
        val query = searchField.text.trim()
        populateList(listPanel, query)
    }

    private fun populateList(listPanel: JPanel, searchQuery: String) {
        listPanel.removeAll()

        val sessionManager = SessionManager.getInstance(project)
        var sessions = sessionManager.listSessions()

        if (searchQuery.isNotBlank()) {
            val lower = searchQuery.lowercase()
            sessions = sessions.filter { session ->
                session.displayTitle().lowercase().contains(lower)
                    || session.firstPrompt.lowercase().contains(lower)
            }
        }

        if (sessions.isEmpty()) {
            val emptyLabel = JBLabel("No sessions found").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.emptyTop(20)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            listPanel.add(emptyLabel)
        } else {
            val groups = groupByTimePeriod(sessions)
            for ((groupName, groupSessions) in groups) {
                addGroupHeader(listPanel, groupName)
                for (session in groupSessions) {
                    addSessionRow(listPanel, session)
                }
            }
        }

        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun groupByTimePeriod(
        sessions: List<SessionInfo>
    ): List<Pair<String, List<SessionInfo>>> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
        val sevenDaysAgo = todayStart - 7L * 24 * 60 * 60 * 1000

        val today = mutableListOf<SessionInfo>()
        val yesterday = mutableListOf<SessionInfo>()
        val lastWeek = mutableListOf<SessionInfo>()
        val older = mutableListOf<SessionInfo>()

        for (session in sessions) {
            val time = session.lastActiveTime
            when {
                time >= todayStart -> today.add(session)
                time >= yesterdayStart -> yesterday.add(session)
                time >= sevenDaysAgo -> lastWeek.add(session)
                else -> older.add(session)
            }
        }

        val result = mutableListOf<Pair<String, List<SessionInfo>>>()
        if (today.isNotEmpty()) result.add("Today" to today)
        if (yesterday.isNotEmpty()) result.add("Yesterday" to yesterday)
        if (lastWeek.isNotEmpty()) result.add("Last 7 Days" to lastWeek)
        if (older.isNotEmpty()) result.add("Older" to older)
        return result
    }

    private fun addGroupHeader(panel: JPanel, title: String) {
        val header = JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(10, 4, 4, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(header)
    }

    private fun addSessionRow(panel: JPanel, session: SessionInfo) {
        val row = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(6, 4, 6, 4)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 60)
            alignmentX = Component.LEFT_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // Left side: status dot + title + timestamp
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        // Status dot + title row
        val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
            .apply { isOpaque = false }
        val stateService = ClaudeStateService.getInstance(project)
        val sessionState = stateService.getSessionState(session.id)
        val dotColor = when (sessionState) {
            ClaudeState.THINKING -> java.awt.Color(0xDA, 0x77, 0x56)
            ClaudeState.WAITING_FOR_PERMISSION ->
                java.awt.Color(0xE1, 0xC0, 0x8D)
            ClaudeState.READY -> java.awt.Color(0x74, 0xC9, 0x91)
        }
        val statusDot = JPanel().apply {
            preferredSize = Dimension(8, 8)
            maximumSize = Dimension(8, 8)
            background = dotColor
            isOpaque = true
        }
        titleRow.add(statusDot)

        val titleLabel = JBLabel(session.displayTitle()).apply {
            font = font.deriveFont(Font.PLAIN, 13f)
        }
        titleRow.add(titleLabel)
        titleRow.alignmentX = Component.LEFT_ALIGNMENT
        infoPanel.add(titleRow)

        val dateFormat = SimpleDateFormat("MMM d, h:mm a")
        val timeStr = dateFormat.format(Date(session.lastActiveTime))
        val modelStr = if (session.model.isNotBlank()) {
            " | ${session.model}"
        } else {
            ""
        }
        val metaLabel = JBLabel("$timeStr$modelStr").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        }
        infoPanel.add(metaLabel)

        // Subtext: first prompt preview
        if (session.firstPrompt.isNotBlank()) {
            val preview = session.firstPrompt.take(80).replace('\n', ' ')
            val subtextLabel = JBLabel(preview).apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            }
            infoPanel.add(subtextLabel)
        }

        row.add(infoPanel, BorderLayout.CENTER)

        // Right side: hover actions (rename + delete)
        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            isOpaque = false
            isVisible = false
        }

        val renameButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = "Rename"
            isBorderPainted = false
            isContentAreaFilled = false
            preferredSize = Dimension(24, 24)
            addActionListener {
                handleRename(session, titleLabel, panel)
            }
        }

        val deleteButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = "Delete"
            isBorderPainted = false
            isContentAreaFilled = false
            preferredSize = Dimension(24, 24)
            addActionListener {
                handleDelete(session, panel)
            }
        }

        actionsPanel.add(renameButton)
        actionsPanel.add(deleteButton)
        row.add(actionsPanel, BorderLayout.EAST)

        // Show/hide actions on hover
        row.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                actionsPanel.isVisible = true
                row.background = JBColor.namedColor(
                    "List.hoverBackground",
                    JBColor(0xEDF6FE, 0x353739)
                )
                row.isOpaque = true
            }

            override fun mouseExited(e: MouseEvent?) {
                actionsPanel.isVisible = false
                row.isOpaque = false
            }

            override fun mouseClicked(e: MouseEvent?) {
                popup?.cancel()
                onResumeSession(session)
            }
        })

        panel.add(row)
    }

    private fun handleRename(
        session: SessionInfo,
        titleLabel: JBLabel,
        listPanel: JPanel
    ) {
        val currentTitle = session.displayTitle()
        val newTitle = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(listPanel),
            "Enter new session name:",
            "Rename Session",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            currentTitle
        ) as? String

        if (newTitle != null && newTitle.isNotBlank()) {
            SessionManager.getInstance(project)
                .renameSession(session.id, newTitle)
            titleLabel.text = newTitle
        }
    }

    private fun handleDelete(
        session: SessionInfo,
        listPanel: JPanel
    ) {
        val confirm = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(listPanel),
            "Delete session \"${session.displayTitle()}\"?",
            "Delete Session",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm == JOptionPane.OK_OPTION) {
            SessionManager.getInstance(project).removeSession(session.id)
            populateList(listPanel, "")
        }
    }
}
