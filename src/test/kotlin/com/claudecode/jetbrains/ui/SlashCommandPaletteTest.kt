package com.claudecode.jetbrains.ui

import com.claudecode.jetbrains.ui.commands.SlashCommandRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandPaletteTest {

    @Test
    fun `empty query returns all commands`() {
        val result = SlashCommandRegistry.filterCommands("")
        assertEquals(SlashCommandRegistry.ALL_COMMANDS.size, result.size)
    }

    @Test
    fun `slash only returns all commands`() {
        val result = SlashCommandRegistry.filterCommands("/")
        assertEquals(SlashCommandRegistry.ALL_COMMANDS.size, result.size)
    }

    @Test
    fun `prefix match filters correctly`() {
        val result = SlashCommandRegistry.filterCommands("cl")
        assertTrue(result.any { it.name == "/clear" })
        assertTrue(result.all { it.name.removePrefix("/").startsWith("cl", ignoreCase = true) })
    }

    @Test
    fun `prefix match with slash`() {
        val result = SlashCommandRegistry.filterCommands("/cl")
        assertTrue(result.any { it.name == "/clear" })
        assertTrue(result.all { it.name.removePrefix("/").startsWith("cl", ignoreCase = true) })
    }

    @Test
    fun `case insensitive filtering`() {
        val lower = SlashCommandRegistry.filterCommands("mo")
        val upper = SlashCommandRegistry.filterCommands("MO")
        assertEquals(lower, upper)
    }

    @Test
    fun `no matches returns empty list`() {
        val result = SlashCommandRegistry.filterCommands("zzz")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `all commands have non-blank descriptions`() {
        for (cmd in SlashCommandRegistry.ALL_COMMANDS) {
            assertTrue("${cmd.name} has blank description", cmd.description.isNotBlank())
        }
    }

    @Test
    fun `single char filters work`() {
        val result = SlashCommandRegistry.filterCommands("m")
        assertTrue(result.any { it.name == "/model" })
        assertTrue(result.any { it.name == "/memory" })
        assertTrue(result.any { it.name == "/mcp" })
    }

    @Test
    fun `all commands have names starting with slash`() {
        for (cmd in SlashCommandRegistry.ALL_COMMANDS) {
            assertTrue("${cmd.name} doesn't start with /", cmd.name.startsWith("/"))
        }
    }

    @Test
    fun `exact name match works`() {
        val result = SlashCommandRegistry.filterCommands("clear")
        assertEquals(1, result.size)
        assertEquals("/clear", result[0].name)
    }
}
