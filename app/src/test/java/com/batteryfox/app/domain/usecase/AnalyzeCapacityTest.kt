package com.batteryfox.app.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class AnalyzeCapacityTest {
    @Test fun emptyEvidenceDoesNotInventAnEstimate() {
        val useCase = AnalyzeCapacity()
        val result = useCase(emptyList())
        assertNull(result.estimate)
        assertEquals(0, result.usableWindowCount)
        assertTrue(useCase.progress(result).contains("at least 3 needed"))
    }
}
