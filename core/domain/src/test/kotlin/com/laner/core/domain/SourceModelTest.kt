package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceModelTest {
    private val now = 1_000_000L

    @Test
    fun `global ai cannot publish fact candidate`() {
        val provenance = SourceProvenance(
            providerId = "local-ai",
            sourceClass = SourceClass.GLOBAL_AI_ASSIST,
            authority = DataAuthority.DERIVED,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = now,
        )

        assertFailsWith<IllegalArgumentException> {
            FactCandidate(
                value = "BLG leads",
                provenance = provenance,
                verification = VerificationState.PROVISIONAL,
            )
        }
    }

    @Test
    fun `inference cannot enter factual arbitration model`() {
        val provenance = SourceProvenance(
            providerId = "provider",
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = DataAuthority.PROVIDER,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = now,
        )

        assertFailsWith<IllegalArgumentException> {
            FactCandidate(
                value = "prediction",
                provenance = provenance,
                verification = VerificationState.INFERENCE,
            )
        }
    }

    @Test
    fun `fact backed ai output requires supporting facts`() {
        assertFailsWith<IllegalArgumentException> {
            AiAssistResult(
                value = "下一波蓝方更主动",
                evidenceKind = AiEvidenceKind.FACT_BACKED,
                generatedAtEpochMillis = now,
            )
        }
    }

    @Test
    fun `realtime provenance becomes stale after target age`() {
        val provenance = SourceProvenance(
            providerId = "live",
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = DataAuthority.PROVIDER,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = now,
        )

        assertTrue(provenance.isWithinFreshnessTarget(now + 15_000L))
        assertTrue(!provenance.isWithinFreshnessTarget(now + 15_001L))
    }
}
