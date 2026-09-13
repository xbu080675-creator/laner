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
import org.json.JSONObject
import java.time.Instant

/**
 * Riot live adapter driven by Riot EventDetails game state.
 *
 * Design intent:
 * 1) EventDetails is the sole authority for which BO game should be bound.
 * 2) A newly selected game is bootstrapped once with /window/{gameId} without startingTime.
 * 3) After bootstrap, aligned LiveStats windows are polled continuously.
 * 4) Historical LiveStats frames never choose the current game.
 *
 * This deliberately mirrors the proven event -> game -> bootstrap -> live-loop architecture used by
 * public LoL Esports viewers, while keeping RiftLab's own models, identity gate and implementation.
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

                // Re-read EventDetails continuously. Stale game state was the root cause of sticking
                // to a completed G2 while G3 had already begun.
                val now = System.currentTimeMillis()
                val games = if (now - lastEventRefreshEpochMs >= EVENT_DETAILS_POLL_MS || selectedGame == null) {
                    try {
                        val refreshed = fetchEventGames(activeEvent)
                        lastEventRefreshEpochMs = now
                        consecutiveEventErrors = 0
                        refreshed
                    } catch (t: Throwable) {
                        consecutiveEventErrors++
                        if (selectedGame == null || consecutiveEventErrors >= MAX_EVENT_ERRORS_WITHOUT_GAME) throw t
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val authoritativeGame = if (games.isNotEmpty()) selectAuthoritativeGame(games) else selectedGame
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
                        message = "Riot EventDriven Live · G${authoritativeGame.gameNumber} gameId 已绑定，开始 bootstrap",
                        eventId = activeEvent.eventId,
                        gameId = authoritativeGame.gameId,
                        lastUpdateEpochMs = System.currentTimeMillis()
                    )
                } else if (games.isNotEmpty()) {
                    // Keep state/side metadata fresh even when the gameId is unchanged.
                    selectedGame = authoritativeGame
                }

                val game = selectedGame ?: continue

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
                        message = "Riot EventDriven Live · G${game.gameNumber} 已绑定，等待第一帧",
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
                    message = "Riot EventDriven Live · G${identified.game} · POLL 1s",
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

                // A transient network failure must not discard a valid event/game binding. Only
                // re-resolve the event after repeated EventDetails failures with no usable game.
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

    private suspend fun fetchEventGames(event: LiveEventRef): List<LiveGameRef> {
        val root = RiotResilientHttp.getJson(
            "${LolEsportsConfig.PERSISTED_BASE}/getEventDetails?hl=en-US&id=${event.eventId}",
            connectTimeoutMs = 5_000,
            readTimeoutMs = 5_000
        )
        val match = root.optJSONObject("data")
            ?.optJSONObject("event")
            ?.optJSONObject("match") ?: return emptyList()
        val games = match.optJSONArray("games") ?: JSONArray()

        return buildList {
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
    }

    /**
     * Same responsibility split as the reference viewer: EventDetails chooses the game.
     * LiveStats never participates in game selection.
     */
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

        // Keep a no-cursor fallback, but never use it to select a different game.
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
        // The public reference viewer polls every 500 ms. Riot LiveStats itself advances on a much
        // coarser timeline, so RiftLab uses 1 s while live and 500 ms only during game handoff.
        private const val HANDOFF_POLL_MS = 500L
        private const val LIVE_POLL_MS = 1_000L
        private const val EVENT_DETAILS_POLL_MS = 1_000L
        private const val IDLE_POLL_MS = 1_000L
        private const val ERROR_POLL_MS = 1_000L
        private const val MAX_EVENT_ERRORS_WITHOUT_GAME = 3
    }
}
