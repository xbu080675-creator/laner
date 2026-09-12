package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.SourceProvenance

/** Provider-neutral verified gameplay frame returned by a LIVE adapter. */
data class ProviderLiveSnapshot(
    val snapshot: LiveGameSnapshot,
    val sourceTimestampEpochMillis: Long? = null,
    val observedAtEpochMillis: Long,
    val revision: Long = sourceTimestampEpochMillis ?: observedAtEpochMillis,
    val sourceUri: String? = null,
) {
    init {
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }
}

interface LiveSnapshotSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readSnapshot(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveSnapshot?>
}

enum class LiveSnapshotLoadStatus {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

data class LiveSnapshotResolution(
    val snapshot: LiveGameSnapshot?,
    val provenance: SourceProvenance?,
    val timelineResult: TimelineIngestResult?,
    val status: LiveSnapshotLoadStatus,
    val selectedProviderId: String?,
    val failures: List<com.laner.core.domain.DiagnosticFailure>,
)
