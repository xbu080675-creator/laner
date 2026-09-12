package com.laner.core.application

import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MatchPhase

enum class Persona {
    SPECTATOR,
    COACH_ANALYST,
}

data class PhaseViewState(
    val phase: MatchPhase,
    val lifecycle: MatchLifecycleState,
    val title: String,
    val summary: String,
    val isLoading: Boolean = false,
    val sourceStatus: String? = null,
)

interface PhaseQuery {
    suspend fun current(persona: Persona): PhaseViewState
}
