package com.claudecode.jetbrains.ui

import com.claudecode.jetbrains.ui.commands.FileMentionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for file mention fuzzy matching and scoring logic.
 * Since FileMentionPicker depends on IntelliJ Project, we test
 * the matching algorithm in isolation here.
 */
class FileMentionPickerTest {

    // Simulate the fuzzy matching logic from FileMentionPicker
    private fun fuzzyContains(text: String, query: String): Boolean {
        var qi = 0
        for (ch in text) {
            if (qi < query.length && ch == query[qi]) {
                qi++
            }
        }
        return qi == query.length
    }

    private fun fuzzyScore(entry: FileMentionEntry, query: String): Int? {
        val path = entry.relativePath.lowercase()
        val name = entry.fileName.lowercase()

        if (path.startsWith(query)) return 1000 + (100 - path.length)
        if (name.startsWith(query)) return 800 + (100 - name.length)
        val pathIdx = path.indexOf(query)
        if (pathIdx >= 0) return 600 + (100 - pathIdx)
        if (fuzzyContains(name, query)) return 400 + (100 - name.length)
        if (fuzzyContains(path, query)) return 200 + (100 - path.length)
        return null
    }

    private fun fuzzyFilter(
        entries: List<FileMentionEntry>,
        query: String
    ): List<FileMentionEntry> {
        val cleanQuery = query.trimEnd('/').lowercase()
        val trailingSlash = query.endsWith("/")

        return entries
            .mapNotNull { entry ->
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

    private val testEntries = listOf(
        FileMentionEntry("src", "src", null, true),
        FileMentionEntry("src/main", "main", null, true),
        FileMentionEntry("src/main/auth", "auth", null, true),
        FileMentionEntry("build.gradle.kts", "build.gradle.kts", null, false),
        FileMentionEntry("src/main/auth/AuthService.kt", "AuthService.kt", null, false),
        FileMentionEntry("src/main/auth/auth.js", "auth.js", null, false),
        FileMentionEntry("src/main/index.ts", "index.ts", null, false),
        FileMentionEntry("src/components/Button.tsx", "Button.tsx", null, false),
        FileMentionEntry("README.md", "README.md", null, false),
        FileMentionEntry("settings.gradle.kts", "settings.gradle.kts", null, false)
    )

    @Test
    fun `empty query returns all entries`() {
        val result = fuzzyFilter(testEntries, "")
        assertEquals(testEntries.size, result.size)
    }

    @Test
    fun `exact filename match scores highest`() {
        val result = fuzzyFilter(testEntries, "build.gradle.kts")
        assertTrue(result.isNotEmpty())
        assertEquals("build.gradle.kts", result[0].fileName)
    }

    @Test
    fun `prefix match on filename works`() {
        val result = fuzzyFilter(testEntries, "build")
        assertTrue(result.isNotEmpty())
        assertEquals("build.gradle.kts", result[0].fileName)
    }

    @Test
    fun `fuzzy match on auth finds multiple files`() {
        val result = fuzzyFilter(testEntries, "auth")
        assertTrue(result.size >= 3)
        val fileNames = result.map { it.fileName }
        assertTrue("auth" in fileNames || "auth.js" in fileNames)
        assertTrue("AuthService.kt" in fileNames)
    }

    @Test
    fun `path prefix match works for src`() {
        val result = fuzzyFilter(testEntries, "src")
        assertTrue(result.isNotEmpty())
        // All src/* entries should match
        assertTrue(result.any { it.relativePath == "src" })
        assertTrue(result.any { it.relativePath.startsWith("src/") })
    }

    @Test
    fun `trailing slash filters to directories and contents`() {
        val result = fuzzyFilter(testEntries, "src/")
        // Should include the src directory and files under src/
        assertTrue(result.isNotEmpty())
        // All results should be directories or paths starting with src/
        for (entry in result) {
            assertTrue(
                "Expected directory or src/ prefix for ${entry.relativePath}",
                entry.isDirectory || entry.relativePath.startsWith("src/")
            )
        }
    }

    @Test
    fun `no match returns empty`() {
        val result = fuzzyFilter(testEntries, "zzzzzzz")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `case insensitive matching`() {
        val lower = fuzzyFilter(testEntries, "readme")
        val upper = fuzzyFilter(testEntries, "README")
        assertTrue(lower.isNotEmpty())
        assertEquals(lower.map { it.relativePath }, upper.map { it.relativePath })
    }

    @Test
    fun `fuzzy contains works for subsequence`() {
        // fuzzyContains is case-sensitive; in production, inputs are lowercased
        assertTrue(fuzzyContains("authservice.kt", "asvk"))
        assertTrue(fuzzyContains("build.gradle.kts", "bgk"))
        assertTrue(fuzzyContains("index.ts", "idx"))
    }

    @Test
    fun `fuzzy contains rejects non-subsequence`() {
        assertTrue(!fuzzyContains("auth.js", "xyz"))
        assertTrue(!fuzzyContains("short", "shortx"))
    }

    @Test
    fun `fuzzy score prefers path prefix over fuzzy match`() {
        val entry1 = FileMentionEntry("build.gradle.kts", "build.gradle.kts", null, false)
        val entry2 = FileMentionEntry("src/main/builder.kt", "builder.kt", null, false)

        val score1 = fuzzyScore(entry1, "build")
        val score2 = fuzzyScore(entry2, "build")

        assertNotNull(score1)
        assertNotNull(score2)
        // entry1 has path prefix match, so it should score higher
        assertTrue("Path prefix should score higher", score1!! > score2!!)
    }

    @Test
    fun `fuzzy score returns null for non-match`() {
        val entry = FileMentionEntry("auth.js", "auth.js", null, false)
        val score = fuzzyScore(entry, "xyz")
        assertNull(score)
    }

    @Test
    fun `FileMentionEntry stores correct properties`() {
        val entry = FileMentionEntry(
            relativePath = "src/main/App.kt",
            fileName = "App.kt",
            icon = null,
            isDirectory = false
        )
        assertEquals("src/main/App.kt", entry.relativePath)
        assertEquals("App.kt", entry.fileName)
        assertEquals(false, entry.isDirectory)
    }

    @Test
    fun `directory entries are correctly identified`() {
        val dir = FileMentionEntry("src", "src", null, true)
        val file = FileMentionEntry("src/main.kt", "main.kt", null, false)
        assertTrue(dir.isDirectory)
        assertTrue(!file.isDirectory)
    }
}
