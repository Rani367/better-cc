package com.claudecode.jetbrains.ui.chat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * A simple fallback panel shown when the user has configured
 * `useTerminal = true` in settings. Displays a button to open
 * Claude in the IDE's integrated terminal.
 */
class TerminalFallbackPanel(
    private val project: Project
) : JPanel(BorderLayout()) {

    private val logger = Logger.getInstance(
        TerminalFallbackPanel::class.java
    )

    init {
        border = JBUI.Borders.empty(20)

        val centerPanel = JPanel(FlowLayout(FlowLayout.CENTER))

        val label = JBLabel(
            "Terminal mode is enabled. Use the terminal for Claude."
        )
        label.border = JBUI.Borders.emptyBottom(12)

        val button = JButton("Open Claude in Terminal")
        button.addActionListener {
            openClaudeInTerminal()
        }

        val verticalPanel = JPanel(BorderLayout())
        verticalPanel.add(label, BorderLayout.NORTH)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        buttonPanel.add(button)
        verticalPanel.add(buttonPanel, BorderLayout.CENTER)

        centerPanel.add(verticalPanel)
        add(centerPanel, BorderLayout.CENTER)
    }

    private fun openClaudeInTerminal() {
        try {
            val cls = Class.forName(
                "org.jetbrains.plugins.terminal.TerminalView"
            )
            val getInstance = cls.getMethod(
                "getInstance", Project::class.java
            )
            val terminalView = getInstance.invoke(null, project)
            val openMethod = cls.getMethod(
                "openTerminalIn", Any::class.java
            )
            openMethod.invoke(terminalView, project.basePath)
        } catch (e: Exception) {
            logger.debug(
                "Terminal plugin not available", e
            )
        }
    }
}
