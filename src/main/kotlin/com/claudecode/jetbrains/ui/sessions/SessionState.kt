package com.claudecode.jetbrains.ui.sessions

import com.claudecode.jetbrains.ui.chat.ChatMessage

/**
 * Bundles the transferable state of a Claude session for teleporting
 * between sidebar and editor tab (or vice versa).
 */
data class SessionState(
    val sessionId: String?,
    val sessionTitle: String,
    val conversationHistory: List<ChatMessage>,
    val messageIndexCounter: Int
)
