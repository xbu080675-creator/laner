package com.laner.core.application

import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline

interface LiveTimelineRepository {
    suspend fun read(gameId: GameId): GameTimeline?
    suspend fun write(timeline: GameTimeline)
}
