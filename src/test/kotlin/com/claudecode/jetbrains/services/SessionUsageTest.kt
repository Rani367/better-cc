package com.claudecode.jetbrains.services

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionUsageTest {

    @Test
    fun `formatTokens shows raw count for small numbers`() {
        val usage = SessionUsage(totalInputTokens = 500, totalOutputTokens = 100)
        assertEquals("600", usage.formatTokens())
    }

    @Test
    fun `formatTokens formats thousands with K suffix`() {
        val usage = SessionUsage(totalInputTokens = 1000, totalOutputTokens = 200)
        assertEquals("1.2K", usage.formatTokens())
    }

    @Test
    fun `formatTokens formats millions with M suffix`() {
        val usage = SessionUsage(
            totalInputTokens = 1_000_000,
            totalOutputTokens = 500_000
        )
        assertEquals("1.5M", usage.formatTokens())
    }

    @Test
    fun `formatCost formats with two decimal places`() {
        val usage = SessionUsage(totalCostUsd = 0.035)
        assertEquals("$0.04", usage.formatCost())
    }

    @Test
    fun `formatCost shows zero cost`() {
        val usage = SessionUsage()
        assertEquals("$0.00", usage.formatCost())
    }

    @Test
    fun `totalTokens sums input and output`() {
        val usage = SessionUsage(totalInputTokens = 300, totalOutputTokens = 200)
        assertEquals(500, usage.totalTokens)
    }
}
