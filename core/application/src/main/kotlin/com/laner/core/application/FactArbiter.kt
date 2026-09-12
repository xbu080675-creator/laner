package com.laner.core.application

import com.laner.core.domain.FactCandidate
import com.laner.core.domain.VerificationState

sealed interface ArbitrationResult<out T> {
    data class Selected<T>(
        val candidate: FactCandidate<T>,
        val alternatives: List<FactCandidate<T>>,
    ) : ArbitrationResult<T>

    data class Conflict<T>(
        val contenders: List<FactCandidate<T>>,
        val reason: String,
    ) : ArbitrationResult<T>

    data object NoData : ArbitrationResult<Nothing>
}

/**
 * One global fact arbiter for all factual source classes.
 *
 * Provider adapters are not allowed to silently overwrite each other. The arbiter first compares
 * verification, then authority, freshness target compliance, source timestamp, and revision.
 */
class FactArbiter(
    private val nowEpochMillis: () -> Long,
) {
    fun <T> choose(candidates: Collection<FactCandidate<T>>): ArbitrationResult<T> {
        if (candidates.isEmpty()) return ArbitrationResult.NoData

        val now = nowEpochMillis()
        val ranked = candidates.sortedWith(candidateComparator(now))
        val best = ranked.first()
        val second = ranked.getOrNull(1)

        if (second != null && best.value != second.value && sameDecisionRank(best, second, now)) {
            return ArbitrationResult.Conflict(
                contenders = ranked.takeWhile { sameDecisionRank(best, it, now) },
                reason = "Equal-ranked factual sources disagree; explicit resolution is required",
            )
        }

        return ArbitrationResult.Selected(
            candidate = best,
            alternatives = ranked.drop(1),
        )
    }

    private fun <T> candidateComparator(now: Long): Comparator<FactCandidate<T>> =
        compareByDescending<FactCandidate<T>> { verificationWeight(it.verification) }
            .thenByDescending { it.provenance.authority.weight }
            .thenByDescending { if (it.provenance.isWithinFreshnessTarget(now)) 1 else 0 }
            .thenByDescending {
                it.provenance.sourceTimestampEpochMillis ?: it.provenance.observedAtEpochMillis
            }
            .thenByDescending { it.provenance.revision }
            .thenBy { it.provenance.providerId }

    private fun <T> sameDecisionRank(
        left: FactCandidate<T>,
        right: FactCandidate<T>,
        now: Long,
    ): Boolean =
        verificationWeight(left.verification) == verificationWeight(right.verification) &&
            left.provenance.authority.weight == right.provenance.authority.weight &&
            left.provenance.isWithinFreshnessTarget(now) == right.provenance.isWithinFreshnessTarget(now) &&
            (left.provenance.sourceTimestampEpochMillis ?: left.provenance.observedAtEpochMillis) ==
            (right.provenance.sourceTimestampEpochMillis ?: right.provenance.observedAtEpochMillis) &&
            left.provenance.revision == right.provenance.revision

    private fun verificationWeight(state: VerificationState): Int = when (state) {
        VerificationState.CONFIRMED -> 30
        VerificationState.PROVISIONAL -> 20
        VerificationState.UNVERIFIED -> 10
        VerificationState.INFERENCE -> error("Inference cannot enter factual arbitration")
    }
}
