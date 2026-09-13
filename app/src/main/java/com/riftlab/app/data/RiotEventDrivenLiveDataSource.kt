package com.riftlab.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.json.JSONArray
import java.time.Instant

/**
 * Riot live adapter driven by Riot EventDetails + Riot LiveStats.
 *
 * Design intent:
 * 1) Direct Riot EventDetails state is authoritative for the active BO game.
 * 2) Persisted-mirror EventDetails is identity-only: it may provide the BO game ids, but its state
 *    is never trusted as real-time truth.
 * 3) In mirror mode, a newer BO game is promoted only after its Riot LiveStats window is meaningful.
 * 4) A newly selected game is bootstrapped once with /window/{gameId} without startingTime.
 * 5) Historical LiveStats frames never move selection backwards.
 *
 * This keeps the proven event -> game -> bootstrap -> live-loop shape used by public LoL Esports
 * viewers while accounting for Android networks where persisted Riot gateway traffic may fall back
 * to RiftLab's GitHub mirror.
 */
internal class RiotEventDrivenLiveDataSource(
    private val client: LolEsportsApiClient = LolEsportsApiClient()
) : LiveMatchDataSource {

    private enum class Stage {
        WAIT_EVENT,
        EVENT_RESOLVED,
        GAME_RESOLVED,
        BOOTSTRAPPING,
        LIVE,
        BETWEEN_GAMES
    }

    private data class EventGamesPayload(
        val games: List<LiveGameRef>,
        val fromMirror: Boolean
    )

    private data class MirrorPromotion(
        val game: LiveGameRef,
        val snapshot: LiveSnapshot
    )

    private val liveCursor = RiotLiveStatsCursor()

    private val _status = MutableStateFlow(
        LiveSourceStatus(
            phase = LiveSourcePhase.IDLE,
            message = "Riot EventDriven Live 尚未启动"
        )
    )
    val status: StateFlow<LiveSourceStatus> = _status.asStateFlow()

    override fun observe(matchId: String): Flow<LiveSnapshot> = flow {
        var stage = Stage.WAIT_EVENT
        var event: LiveEventRef? = null
        var selectedGame: LiveGameRef? = null
        var selectedGameId = ""
        var previous: LiveSnapshot? = null
        var observedTargetKey = ""
        var lastEventRefreshEpochMs = 0L
        var lastEmissionKey = ""
        var consecutiveEventErrors = 0
        var latestGames = emptyList<LiveGameRef>()
        var eventDetailsFromMirror = false

        while (currentCoroutineContext().isActive) {
            val loopStarted = System.currentTimeMillis()
            try {
                val target = LiveMatchTargetRegistry.snapshot()
                val targetKey = LiveMatchTargetRegistry.key(target)
                if (targetKey != observedTargetKey) {
                    observedTargetKey = targetKey
                    event = null
                    selectedGame = null
                    selectedGameId = ""
                    previous = null
                    latestGames = emptyList()
                    eventDetailsFromMirror = false
                    lastEventRefreshEpochMs = 0L
                    lastEmissionKey = ""
                    consecutiveEventErrors = 0
                    liveCursor.reset()
                    stage = Stage.WAIT_EVENT
                }

                if (target != null && isExternalProviderTarget(target)) {
                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.WAITING_FOR_MATCH,
                        message = "Riot EventDriven Live · 当前赛事不是 Riot Event，等待其它 Provider",
                        eventId = target.eventId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                    delay(IDLE_POLL_MS)
                    continue
                }

                if (event == null) {
                    event = resolveEvent(matchId, target)
                    if (event == null) {
                        stage = Stage.WAIT_EVENT
                        _status.value = LiveSourceStatus(
                            phase = LiveSourcePhase.WAITING_FOR_MATCH,
                            message = "Riot EventDriven Live · 等待 Riot Event",
                            eventId = target?.eventId.orEmpty(),
                            lastUpdateEpochMs = System.currentTimeMillis()
                        )
                        delay(IDLE_POLL_MS)
                        continue
                    }
                    stage = Stage.EVENT_RESOLVED
                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.WAITING_FOR_MATCH,
                        message = "Riot EventDriven Live · Event 已锁定，解析当前小局",
                        eventId = event!!.eventId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                }

                val activeEvent = event ?: continue
                val now = System.currentTimeMillis()
                var refreshedThisLoop = false
                if (now - lastEventRefreshEpochMs >= EVENT_DETAILS_POLL_MS || selectedGame == null) {
                    try {
                        val refreshed = fetchEventGames(activeEvent)
                        lastEventRefreshEpochMs = now
                        consecutiveEventErrors = 0
                        if (refreshed.games.isNotEmpty()) {
                            latestGames = refreshed.games
                            eventDetailsFromMirror = refreshed.fromMirror
                            refreshedThisLoop = true
                        }
                    } catch (t: Throwable) {
                        consecutiveEventErrors++
                        if (selectedGame == null || consecutiveEventErrors >= MAX_EVENT_ERRORS_WITHOUT_GAME) throw t
                    }
                }

                val authoritativeGame = when {
                    latestGames.isEmpty() -> selectedGame
                    eventDetailsFromMirror -> selectMirrorAnchorGame(latestGames, target, selectedGame)
                    else -> selectAuthoritativeGame(latestGames)
                }

                if (authoritativeGame == null) {
                    stage = Stage.BETWEEN_GAMES
                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.BETWEEN_GAMES,
                        message = "Riot EventDriven Live · EventDetails 暂无可绑定小局",
                        eventId = activeEvent.eventId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                    delay(HANDOFF_POLL_MS)
                    continue
                }

                if (authoritativeGame.gameId != selectedGameId) {
                    selectedGame = authoritativeGame
                    selectedGameId = authoritativeGame.gameId
                    previous = null
                    lastEmissionKey = ""
                    liveCursor.reset()
                    stage = Stage.GAME_RESOLVED

                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.WAITING_FOR_MATCH,
                        message = if (eventDetailsFromMirror) {
                            "Riot EventDriven Live · MIRROR IDs · G${authoritativeGame.gameNumber} 已绑定，LiveStats 验证中"
                        } else {
                            "Riot EventDriven Live · DIRECT · G${authoritativeGame.gameNumber} gameId 已绑定，开始 bootstrap"
                        },
                        eventId = activeEvent.eventId,
                        gameId = authoritativeGame.gameId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                } else if (refreshedThisLoop) {
                    selectedGame = authoritativeGame
                }

                var game = selectedGame ?: continue

                // Mirror EventDetails can be minutes behind. Treat its state as stale and use the
                // static BO game ids only. Probe only *newer* games; promotion requires a meaningful
                // Riot LiveStats frame, so historical frames can never move us backwards.
                if (eventDetailsFromMirror && latestGames.isNotEmpty()) {
                    val promotion = findNewerMirrorLiveGame(activeEvent, game, latestGames)
                    if (promotion != null && promotion.game.gameId != game.gameId) {
                        selectedGame = promotion.game
                        selectedGameId = promotion.game.gameId
                        game = promotion.game
                        previous = promotion.snapshot
                        lastEmissionKey = ""
                        liveCursor.reset()
                        liveCursor.onSuccess()
                        stage = Stage.LIVE

                        val identifiedPromotion = promotion.snapshot.copy(targetKey = observedTargetKey)
                        val currentTarget = LiveMatchTargetRegistry.snapshot()
                        if (currentTarget != null && MatchIdentityPolicy.snapshotBelongsTo(identifiedPromotion, currentTarget)) {
                            previous = identifiedPromotion
                            val promotionKey = emissionKey(identifiedPromotion)
                            _status.value = LiveSourceStatus(
                                phase = LiveSourcePhase.LIVE,
                                message = "Riot EventDriven Live · MIRROR IDs → LiveStats verified G${identifiedPromotion.game} · POLL 1s",
                                eventId = activeEvent.eventId,
                                gameId = identifiedPromotion.gameId,
                                lastUpdateEpochMs = System.currentTimeMillis()
                            )
                            if (promotionKey != lastEmissionKey) {
                                lastEmissionKey = promotionKey
                                emit(identifiedPromotion)
                            }
                            delayRemaining(loopStarted, LIVE_POLL_MS)
                            continue
                        }
                    }
                }

                val snapshot = if (stage == Stage.GAME_RESOLVED || stage == Stage.BOOTSTRAPPING) {
                    stage = Stage.BOOTSTRAPPING
                    bootstrap(activeEvent, game, previous)
                } else {
                    fetchLatest(activeEvent, game, previous)
                }

                if (snapshot == null) {
                    stage = Stage.BOOTSTRAPPING
                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.WAITING_FOR_MATCH,
                        message = if (eventDetailsFromMirror) {
                            "Riot EventDriven Live · MIRROR IDs · G${game.gameNumber} 已绑定，等待 LiveStats 第一帧"
                        } else {
                            "Riot EventDriven Live · G${game.gameNumber} 已绑定，等待第一帧"
                        },
                        eventId = activeEvent.eventId,
                        gameId = game.gameId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                    delayRemaining(loopStarted, HANDOFF_POLL_MS)
                    continue
                }

                val identified = snapshot.copy(targetKey = observedTargetKey)
                val currentTarget = LiveMatchTargetRegistry.snapshot()
                if (currentTarget == null || !MatchIdentityPolicy.snapshotBelongsTo(identified, currentTarget)) {
                    _status.value = LiveSourceStatus(
                        phase = LiveSourcePhase.WAITING_FOR_MATCH,
                        message = "Riot EventDriven Live · 当前帧未通过赛程身份门禁，保留 gameId 等下一帧",
                        eventId = activeEvent.eventId,
                        gameId = game.gameId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                    delayRemaining(loopStarted, HANDOFF_POLL_MS)
                    continue
                }

                previous = identified
                stage = Stage.LIVE
                val emissionKey = emissionKey(identified)
                _status.value = LiveSourceStatus(
                    phase = LiveSourcePhase.LIVE,
                    message = if (eventDetailsFromMirror) {
                        "Riot EventDriven Live · MIRROR IDs + LiveStats · G${identified.game} · POLL 1s"
                    } else {
                        "Riot EventDriven Live · DIRECT · G${identified.game} · POLL 1s"
                    },
                    eventId = activeEvent.eventId,
                    gameId = identified.gameId,
                    lastUpdateEpochMs = System.currentTimeMillis()
                )
                if (emissionKey != lastEmissionKey) {
                    lastEmissionKey = emissionKey
                    emit(identified)
                }

                delayRemaining(loopStarted, LIVE_POLL_MS)
            } catch (t: Throwable) {
                val activeEventId = event?.eventId.orEmpty()
                val activeGameId = selectedGameId
                _status.value = LiveSourceStatus(
                    phase = LiveSourcePhase.ERROR,
                    message = "Riot EventDriven Live · ${t.message?.take(140) ?: t::class.java.simpleName}",
                    eventId = activeEventId,
                    gameId = activeGameId,
                    lastUpdateEpochMs = System.currentTimeMillis()
                )

                if (selectedGame == null && consecutiveEventErrors >= MAX_EVENT_ERRORS_WITHOUT_GAME) {
                    event = null
                    lastEventRefreshEpochMs = 0L
                    consecutiveEventErrors = 0
                    stage = Stage.WAIT_EVENT
                }
                delayRemaining(loopStarted, ERROR_POLL_MS)
            }
        }
    }

    private suspend fun resolveEvent(
        matchId: String,
        target: ScheduledEsportsMatch?
    ): LiveEventRef? {
        if (target != null && target.eventId.isNotBlank()) {
            return LiveEventRef(
                eventId = target.eventId,
                matchId = target.matchId,
                startTimeIso = target.startTimeIso,
                teams = target.teams
            )
        }
        return client.findLiveEvent(preferredMatchId = matchId)
    }

    private suspend fun fetchEventGames(event: LiveEventRef): EventGamesPayload {
        val root = RiotResilientHttp.getJson(
            "${LolEsportsConfig.PERSISTED_BASE}/getEventDetails?hl=en-US&id=${event.eventId}",
            connectTimeoutMs = 5_000,
            readTimeoutMs = 5_000
        )
        val fromMirror = RiotResilientHttp.sourceLabel().startsWith("RiftLab Riot Mirror")
        val match = root.optJSONObject("data")
            ?.optJSONObject("event")
            ?.optJSONObject("match") ?: return EventGamesPayload(emptyList(), fromMirror)
        val games = match.optJSONArray("games") ?: JSONArray()

        val parsed = buildList {
            for (i in 0 until games.length()) {
                val raw = games.optJSONObject(i) ?: continue
                val gameId = raw.optString("id")
                if (gameId.isBlank()) continue
                var blueId = ""
                var redId = ""
                val teams = raw.optJSONArray("teams") ?: JSONArray()
                for (j in 0 until teams.length()) {
                    val team = teams.optJSONObject(j) ?: continue
                    when (team.optString("side").lowercase()) {
                        "blue" -> blueId = team.optString("id")
                        "red" -> redId = team.optString("id")
                    }
                }
                add(
                    LiveGameRef(
                        gameId = gameId,
                        gameNumber = raw.optInt("number", i + 1),
                        state = raw.optString("state"),
                        blueTeamId = blueId,
                        redTeamId = redId,
                        teams = event.teams
                    )
                )
            }
        }.sortedBy { it.gameNumber }

        return EventGamesPayload(parsed, fromMirror)
    }

    /** Direct EventDetails state is authoritative when the request actually came from Riot. */
    private fun selectAuthoritativeGame(games: List<LiveGameRef>): LiveGameRef? {
        if (games.isEmpty()) return null

        games
            .filter { normalizeState(it.state).contains("progress") }
            .maxByOrNull { it.gameNumber }
            ?.let { return it }

        games
            .firstOrNull { normalizeState(it.state).contains("unstarted") }
            ?.let { return it }

        return games
            .filter { stateIsCompleted(it.state) }
            .maxByOrNull { it.gameNumber }
    }

    /**
     * Mirror state is not real-time truth. Prefer the current schedule score as a soft anchor when
     * it has advanced, otherwise retain the current binding, otherwise fall back to mirror state.
     * Any actual advancement beyond this anchor must be verified by Riot LiveStats.
     */
    private fun selectMirrorAnchorGame(
        games: List<LiveGameRef>,
        target: ScheduledEsportsMatch?,
        selected: LiveGameRef?
    ): LiveGameRef? {
        if (games.isEmpty()) return selected

        val scoreHint = target
            ?.teams
            ?.take(2)
            ?.sumOf { it.gameWins }
            ?.plus(1)
            ?.coerceAtLeast(1)
            ?: 1

        val hinted = games.firstOrNull { it.gameNumber == scoreHint }
        if (selected != null) {
            if (hinted != null && hinted.gameNumber > selected.gameNumber) return hinted
            return games.firstOrNull { it.gameId == selected.gameId } ?: selected
        }
        if (hinted != null && scoreHint > 1) return hinted
        return selectAuthoritativeGame(games) ?: games.firstOrNull()
    }

    /**
     * In mirror mode only, probe newer BO game ids from newest to oldest. A game is promoted only if
     * Riot LiveStats returns a meaningful frame. This is what lets G4 take over even when a cached
     * EventDetails payload still claims G3 is in progress.
     */
    private suspend fun findNewerMirrorLiveGame(
        event: LiveEventRef,
        current: LiveGameRef,
        games: List<LiveGameRef>
    ): MirrorPromotion? {
        val newer = games
            .filter { it.gameNumber > current.gameNumber }
            .sortedByDescending { it.gameNumber }
        for (candidate in newer) {
            val snapshot = bootstrap(event, candidate, previous = null) ?: continue
            return MirrorPromotion(candidate, snapshot)
        }
        return null
    }

    /**
     * Bootstrap intentionally omits startingTime. This asks Riot for the game's initial available
     * window and metadata before the aligned cursor loop begins.
     */
    private suspend fun bootstrap(
        event: LiveEventRef,
        game: LiveGameRef,
        previous: LiveSnapshot?
    ): LiveSnapshot? {
        return try {
            val snapshot = client.fetchLiveWindow(event.copy(startTimeIso = ""), game, previous)
            if (isMeaningful(snapshot)) {
                liveCursor.reset()
                liveCursor.onSuccess()
                snapshot
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchLatest(
        event: LiveEventRef,
        game: LiveGameRef,
        previous: LiveSnapshot?
    ): LiveSnapshot? {
        var latestError: Throwable? = null
        for (startingTime in liveCursor.candidates(Instant.now())) {
            try {
                val snapshot = client.fetchLiveWindow(event.copy(startTimeIso = startingTime), game, previous)
                if (isMeaningful(snapshot)) {
                    liveCursor.onSuccess()
                    return snapshot
                }
            } catch (t: Throwable) {
                latestError = t
            }
        }

        try {
            val fallback = client.fetchLiveWindow(event.copy(startTimeIso = ""), game, previous)
            if (isMeaningful(fallback)) {
                liveCursor.onSuccess()
                return fallback
            }
        } catch (t: Throwable) {
            latestError = t
        }

        liveCursor.onMiss()
        if (latestError is java.io.IOException) return null
        return null
    }

    private fun emissionKey(snapshot: LiveSnapshot): String = buildString {
        append(snapshot.gameId).append('|')
        append(snapshot.elapsedSeconds).append('|')
        append(snapshot.blueGold).append('|').append(snapshot.redGold).append('|')
        append(snapshot.blueKills).append('|').append(snapshot.redKills).append('|')
        append(snapshot.blueTowers).append('|').append(snapshot.redTowers).append('|')
        append(snapshot.blueDragons).append('|').append(snapshot.redDragons).append('|')
        append(snapshot.blueBarons).append('|').append(snapshot.redBarons)
    }

    private fun isMeaningful(snapshot: LiveSnapshot): Boolean =
        snapshot.blueGold > 0 ||
            snapshot.redGold > 0 ||
            snapshot.blueKills > 0 ||
            snapshot.redKills > 0 ||
            snapshot.bluePlayers.any { it.gold > 0 || it.creepScore > 0 || it.level > 1 } ||
            snapshot.redPlayers.any { it.gold > 0 || it.creepScore > 0 || it.level > 1 }

    private fun normalizeState(value: String): String =
        value.lowercase().replace("_", "").replace("-", "").replace(" ", "")

    private fun stateIsCompleted(value: String): Boolean {
        val state = normalizeState(value)
        return state.contains("complete") || state == "finished"
    }

    private fun isExternalProviderTarget(match: ScheduledEsportsMatch): Boolean =
        match.eventId.startsWith("provider:") || match.leagueId.startsWith("rft-event:")

    private suspend fun delayRemaining(startedAt: Long, periodMs: Long) {
        val spent = System.currentTimeMillis() - startedAt
        if (spent < periodMs) delay(periodMs - spent)
    }

    companion object {
        private const val HANDOFF_POLL_MS = 500L
        private const val LIVE_POLL_MS = 1_000L
        private const val EVENT_DETAILS_POLL_MS = 1_000L
        private const val IDLE_POLL_MS = 1_000L
        private const val ERROR_POLL_MS = 1_000L
        private const val MAX_EVENT_ERRORS_WITHOUT_GAME = 3
    }
}
