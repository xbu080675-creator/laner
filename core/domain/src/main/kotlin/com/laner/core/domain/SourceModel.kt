package com.laner.core.domain

enum class SourceClass {
    PRE_MATCH_SOURCE,
    LIVE_MATCH_SOURCE,
    POST_MATCH_SOURCE,
    GLOBAL_AI_ASSIST,
}

enum class DataAuthority(val weight: Int) {
    OFFICIAL(600),
    VERIFIED_PROVIDER(500),
    PROVIDER(400),
    USER_INPUT(350),
    DERIVED(300),
    LOCAL_CACHE(200),
    APK_SEED(100),
}

enum class FreshnessClass(val targetMaxAgeMillis: Long?) {
    STATIC(null),
    DAILY(24L * 60 * 60 * 1000),
    HOURLY(60L * 60 * 1000),
    MINUTES(5L * 60 * 1000),
    REALTIME(15L * 1000),
}

enum class VerificationState {
    CONFIRMED,
    PROVISIONAL,
    INFERENCE,
    UNVERIFIED,
}

enum class AiEvidenceKind {
    FACT_BACKED,
    INFERENCE,
    UNVERIFIED,
}

data class SourceProvenance(
    val providerId: String,
    val sourceClass: SourceClass,
    val authority: DataAuthority,
    val freshnessClass: FreshnessClass,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val revision: Long = 0,
    val sourceUri: String? = null,
) {
    init {
        require(providerId.isNotBlank())
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }

    fun ageMillis(nowEpochMillis: Long): Long {
        val timestamp = sourceTimestampEpochMillis ?: observedAtEpochMillis
        return (nowEpochMillis - timestamp).coerceAtLeast(0)
    }

    fun isWithinFreshnessTarget(nowEpochMillis: Long): Boolean {
        val maxAge = freshnessClass.targetMaxAgeMillis ?: return true
        return ageMillis(nowEpochMillis) <= maxAge
    }
}

data class FactCandidate<T>(
    val value: T,
    val provenance: SourceProvenance,
    val verification: VerificationState,
) {
    init {
        require(provenance.sourceClass != SourceClass.GLOBAL_AI_ASSIST) {
            "GLOBAL_AI_ASSIST cannot publish authoritative fact candidates"
        }
        require(verification != VerificationState.INFERENCE) {
            "Inference must not enter the factual source arbitration path"
        }
    }
}

data class AiAssistResult<T>(
    val value: T,
    val evidenceKind: AiEvidenceKind,
    val supportingFactIds: Set<String> = emptySet(),
    val generatedAtEpochMillis: Long,
) {
    init {
        require(generatedAtEpochMillis >= 0)
        if (evidenceKind == AiEvidenceKind.FACT_BACKED) {
            require(supportingFactIds.isNotEmpty()) {
                "FACT_BACKED AI output must reference at least one supporting fact"
            }
        }
    }
}
