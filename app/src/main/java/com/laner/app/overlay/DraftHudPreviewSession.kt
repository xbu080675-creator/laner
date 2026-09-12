package com.laner.app.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PreviewDraftSide { LEFT, RIGHT }
enum class PreviewDraftRole(val label: String) {
    TOP("上路"),
    JUNGLE("打野"),
    MID("中路"),
    BOT("下路"),
    SUPPORT("辅助"),
}

data class PreviewDraftPick(
    val side: PreviewDraftSide,
    val role: PreviewDraftRole,
    val champion: String,
)

data class DraftHudPreviewState(
    val active: Boolean = false,
    val autoPlay: Boolean = false,
    val step: Int = 0,
    val totalSteps: Int = 10,
    val presentation: DraftHudPresentation = DraftHudPresentation.inactive(),
)

/**
 * Local-only visual fixture for adjusting the Android Draft HUD without waiting for a real match.
 * It never enters Core, repositories, source arbitration, or Timeline.
 */
object DraftHudPreviewSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    private val script = listOf(
        PreviewDraftPick(PreviewDraftSide.LEFT, PreviewDraftRole.TOP, "加里奥"),
        PreviewDraftPick(PreviewDraftSide.RIGHT, PreviewDraftRole.TOP, "兰博"),
        PreviewDraftPick(PreviewDraftSide.LEFT, PreviewDraftRole.JUNGLE, "潘森"),
        PreviewDraftPick(PreviewDraftSide.RIGHT, PreviewDraftRole.JUNGLE, "奇亚娜"),
        PreviewDraftPick(PreviewDraftSide.LEFT, PreviewDraftRole.MID, "辛德拉"),
        PreviewDraftPick(PreviewDraftSide.RIGHT, PreviewDraftRole.MID, "瑞兹"),
        PreviewDraftPick(PreviewDraftSide.LEFT, PreviewDraftRole.BOT, "伊泽瑞尔"),
        PreviewDraftPick(PreviewDraftSide.RIGHT, PreviewDraftRole.BOT, "艾希"),
        PreviewDraftPick(PreviewDraftSide.LEFT, PreviewDraftRole.SUPPORT, "卡尔玛"),
        PreviewDraftPick(PreviewDraftSide.RIGHT, PreviewDraftRole.SUPPORT, "萨勒芬妮"),
    )

    private val _state = MutableStateFlow(DraftHudPreviewState(totalSteps = script.size))
    val state: StateFlow<DraftHudPreviewState> = _state.asStateFlow()

    @Synchronized
    fun startAuto() {
        playbackJob?.cancel()
        reset(active = true, autoPlay = true)
        playbackJob = scope.launch {
            delay(700)
            while (_state.value.active && _state.value.autoPlay && _state.value.step < script.size) {
                advanceInternal()
                if (_state.value.active && _state.value.autoPlay) delay(3_000)
            }
        }
    }

    @Synchronized
    fun startManual() {
        playbackJob?.cancel()
        reset(active = true, autoPlay = false)
    }

    @Synchronized
    fun next() {
        if (!_state.value.active) reset(active = true, autoPlay = false)
        if (_state.value.autoPlay) {
            playbackJob?.cancel()
            _state.value = _state.value.copy(autoPlay = false)
        }
        advanceInternal()
    }

    @Synchronized
    fun toggleAuto() {
        val current = _state.value
        if (!current.active || current.step >= script.size) {
            startAuto()
            return
        }
        if (current.autoPlay) {
            playbackJob?.cancel()
            _state.value = current.copy(autoPlay = false)
        } else {
            _state.value = current.copy(autoPlay = true)
            playbackJob = scope.launch {
                while (_state.value.active && _state.value.autoPlay && _state.value.step < script.size) {
                    delay(3_000)
                    advanceInternal()
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _state.value = DraftHudPreviewState(totalSteps = script.size)
    }

    private fun reset(active: Boolean, autoPlay: Boolean) {
        _state.value = DraftHudPreviewState(
            active = active,
            autoPlay = autoPlay,
            step = 0,
            totalSteps = script.size,
            presentation = buildPresentation(emptyList(), autoPlay),
        )
    }

    @Synchronized
    private fun advanceInternal() {
        val current = _state.value
        if (!current.active || current.step >= script.size) return
        val nextStep = current.step + 1
        val picks = script.take(nextStep)
        val finished = nextStep >= script.size
        _state.value = current.copy(
            autoPlay = current.autoPlay && !finished,
            step = nextStep,
            presentation = buildPresentation(picks, current.autoPlay && !finished),
        )
    }

    private fun buildPresentation(picks: List<PreviewDraftPick>, autoPlay: Boolean): DraftHudPresentation {
        val left = picks.filter { it.side == PreviewDraftSide.LEFT }
        val right = picks.filter { it.side == PreviewDraftSide.RIGHT }
        val latest = picks.lastOrNull()
        val paired = latest?.role?.let { role ->
            val l = left.lastOrNull { it.role == role }
            val r = right.lastOrNull { it.role == role }
            if (l != null && r != null) "${role.label} · ${l.champion} ↔ ${r.champion}" else null
        }
        val finished = picks.size >= script.size
        return DraftHudPresentation(
            active = true,
            sourceMode = DraftHudSourceMode.PREVIEW,
            leftTeam = "AL",
            rightTeam = "BLG",
            leftPicks = left.map { it.champion },
            rightPicks = right.map { it.champion },
            leftBans = emptyList(),
            rightBans = emptyList(),
            latestAction = latest?.let {
                "${if (it.side == PreviewDraftSide.LEFT) "AL" else "BLG"} · ${it.role.label} · 锁定 ${it.champion}"
            } ?: "本地 HUD 演示待机",
            matchup = paired,
            step = picks.size,
            totalSteps = script.size,
            message = when {
                finished -> "本地 HUD 演示已完成 · 不属于赛事事实"
                autoPlay -> "本地 HUD 自动演示 · 不属于赛事事实"
                else -> "本地 HUD 手动演示 · 不属于赛事事实"
            },
            sourceLabel = "LOCAL PREVIEW · NOT FACT",
        )
    }
}
