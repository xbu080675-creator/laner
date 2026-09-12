package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FactCandidate
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FactArbiterTest {
    private val now = 10_000_000L
    private val arbiter = FactArbiter { now }

    @Test
    fun `confirmed official beats newer provisional provider`() {
        val official = candidate(
            value = "BLG",
            provider = "official",
            authority = DataAuthority.OFFICIAL,
            verification = VerificationState.CONFIRMED,
            observedAt = now - 10_000L,
        )
        val fastProvider = candidate(
            value = "IG",
            provider = "fast",
            authority = DataAuthority.PROVIDER,
            verification = VerificationState.PROVISIONAL,
            observedAt = now,
        )

        val result = assertIs<ArbitrationResult.Selected<String>>(arbiter.choose(listOf(fastProvider, official)))
        assertEquals("BLG", result.candidate.value)
    }

    @Test
    fun `same authority prefers fresh candidate`() {
        val stale = candidate(
            value = "old",
            provider = "a",
            authority = DataAuthority.PROVIDER,
            verification = VerificationState.PROVISIONAL,
            observedAt = now - 60_000L,
        )
        val fresh = candidate(
            value = "fresh",
            provider = "b",
            authority = DataAuthority.PROVIDER,
            verification = VerificationState.PROVISIONAL,
            observedAt = now,
        )

        val result = assertIs<ArbitrationResult.Selected<String>>(arbiter.choose(listOf(stale, fresh)))
        assertEquals("fresh", result.candidate.value)
    }

    @Test
    fun `equal ranked disagreement is explicit conflict`() {
        val left = candidate(
            value = "A",
            provider = "provider-a",
            authority = DataAuthority.VERIFIED_PROVIDER,
            verification = VerificationState.CONFIRMED,
            observedAt = now,
            revision = 4,
        )
        val right = candidate(
            value = "B",
            provider = "provider-b",
            authority = DataAuthority.VERIFIED_PROVIDER,
            verification = VerificationState.CONFIRMED,
            observedAt = now,
            revision = 4,
        )

        val result = assertIs<ArbitrationResult.Conflict<String>>(arbiter.choose(listOf(left, right)))
        assertEquals(2, result.contenders.size)
    }

    @Test
    fun `higher revision wins when all earlier ranks match`() {
        val revision1 = candidate(
            value = "old",
            provider = "same-provider",
            authority = DataAuthority.OFFICIAL,
            verification = VerificationState.CONFIRMED,
            observedAt = now,
            revision = 1,
        )
        val revision2 = candidate(
            value = "corrected",
            provider = "same-provider",
            authority = DataAuthority.OFFICIAL,
            verification = VerificationState.CONFIRMED,
            observedAt = now,
            revision = 2,
        )

        val result = assertIs<ArbitrationResult.Selected<String>>(arbiter.choose(listOf(revision1, revision2)))
        assertEquals("corrected", result.candidate.value)
    }

    private fun candidate(
        value: String,
        provider: String,
        authority: DataAuthority,
        verification: VerificationState,
        observedAt: Long,
        revision: Long = 0,
    ) = FactCandidate(
        value = value,
        provenance = SourceProvenance(
            providerId = provider,
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = authority,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = observedAt,
            revision = revision,
        ),
        verification = verification,
    )
}
