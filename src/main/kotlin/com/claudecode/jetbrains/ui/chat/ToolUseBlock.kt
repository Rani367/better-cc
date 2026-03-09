package com.claudecode.jetbrains.ui.chat

enum class ToolUseStatus {
    RUNNING,
    COMPLETE
}

enum class ToolCategory {
    READ,
    WRITE,
    BASH,
    OTHER;

    companion object {
        fun categorize(toolName: String): ToolCategory = when (toolName) {
            "Read", "Glob", "Grep" -> READ
            "Write", "Edit" -> WRITE
            "Bash" -> BASH
            else -> OTHER
        }
    }
}

data class ToolUseState(
    val toolUseId: String,
    val toolName: String,
    val inputJson: StringBuilder = StringBuilder(),
    var status: ToolUseStatus = ToolUseStatus.RUNNING,
    val category: ToolCategory = ToolCategory.categorize(toolName)
) {
    fun summary(): String {
        val json = inputJson.toString()
        if (json.isBlank()) return toolName

        return try {
            extractField(json)
        } catch (_: Exception) {
            toolName
        }
    }

    private fun extractField(json: String): String {
        return when (toolName) {
            "Read" -> extractJsonString(json, "file_path") ?: toolName
            "Write" -> extractJsonString(json, "file_path") ?: toolName
            "Edit" -> extractJsonString(json, "file_path") ?: toolName
            "Bash" -> {
                val cmd = extractJsonString(json, "command") ?: return toolName
                if (cmd.length > 60) cmd.take(57) + "..." else cmd
            }
            "Glob" -> extractJsonString(json, "pattern") ?: toolName
            "Grep" -> extractJsonString(json, "pattern") ?: toolName
            else -> toolName
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        // Try simple regex extraction — works for both complete and partial JSON
        val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }
}
