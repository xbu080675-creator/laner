package com.laner.core.application

import com.laner.core.domain.FactCandidate
import com.laner.core.domain.SourceClass

enum class SourceHealth {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
}

data class SourceRequestContext(
    val nowEpochMillis: Long,
    val correlationId: String,
) {
    init {
        require(nowEpochMillis >= 0)
        require(correlationId.isNotBlank())
    }
}

sealed interface SourceRead<out T> {
    data class Success<T>(val candidates: List<FactCandidate<T>>) : SourceRead<T>
    data class Failure(val errorCode: String, val message: String, val retryable: Boolean) : SourceRead<Nothing> {
        init {
            require(errorCode.isNotBlank())
            require(message.isNotBlank())
        }
    }
}

/**
 * Provider adapter boundary. Implementations translate external APIs into domain fact candidates.
 */
interface FactSourcePort<Q, T> {
    val providerId: String
    val sourceClass: SourceClass
    val health: SourceHealth

    suspend fun read(query: Q, context: SourceRequestContext): SourceRead<T>
}

/**
 * Persistence is abstract here; Room/SQLite/JSON/file implementations stay outside Core.
 */
interface FactRepository<K, T> {
    suspend fun read(key: K): FactCandidate<T>?
    suspend fun write(key: K, candidate: FactCandidate<T>)
}
