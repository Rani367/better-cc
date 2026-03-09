package com.claudecode.jetbrains.ui.chat

import java.time.LocalDateTime
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT,
    ERROR
}

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val id: String = UUID.randomUUID().toString()
)
