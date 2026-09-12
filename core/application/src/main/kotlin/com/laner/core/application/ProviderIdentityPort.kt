package com.laner.core.application

import com.laner.core.domain.MatchId

data class ProviderMatchIdentity(
    val providerId: String,
    val matchId: MatchId,
    val externalEventId: String? = null,
    val externalMatchId: String? = null,
    val observedAtEpochMillis: Long,
) {
    init {
        require(providerId.isNotBlank())
        require(externalEventId == null || externalEventId.isNotBlank())
        require(externalMatchId == null || externalMatchId.isNotBlank())
        require(externalEventId != null || externalMatchId != null)
        require(observedAtEpochMillis >= 0)
    }
}

/**
 * Canonical <-> provider match identity mapping boundary.
 *
 * Provider IDs never become Domain IDs. PRE adapters can publish mappings when they normalize a
 * provider schedule row; LIVE/POST adapters can later resolve the provider ID for the same canonical
 * match without guessing from MatchId strings.
 */
interface ProviderMatchIdentityRepository {
    suspend fun upsert(identity: ProviderMatchIdentity)
    suspend fun find(providerId: String, matchId: MatchId): ProviderMatchIdentity?
}
