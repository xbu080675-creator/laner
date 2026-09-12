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

data class TacticalHudPreviewState(
    val active: Boolean = false,
    val autoPlay: Boolean = false,
    val step: Int = 0,
    val totalSteps: Int = 3,
    val presentation: TacticalHudPresentation = TacticalHudPresentation.inactive(),
)

/** Local-only visual fixture. It never enters Core, source arbitration, repositories or Timeline. */
object TacticalHudPreviewSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    private val script = listOf(
        TacticalHudPresentation(
            active = true,
            sourceMode = TacticalHudSourceMode.PREVIEW,
            phase = TacticalHudPhase.FIGHT,
            clock = "17:08",
            matchLabel = "AL vs BLG · G1",
            headline = "团战窗口 · AL +1 / BLG +3",
            explanation = "本地视觉演示：仅检查全局布局与信息层级，不属于赛事事实。",
            evidence = listOf(
                TacticalHudEvidence("EVIDENCE", "DERIVED_WINDOW"),
                TacticalHudEvidence("WINDOW", "12s"),
                TacticalHudEvidence("KILL Δ", "+4"),
            ),
            players = listOf(
                TacticalHudPlayer("knight", "Syndra", "Lv11 · 4/1/3 · CS 176"),
            ),
            sourceLabel = "LOCAL PREVIEW · NOT FACT",
        ),
        TacticalHudPresentation(
            active = true,
            sourceMode = TacticalHudSourceMode.PREVIEW,
            phase = TacticalHudPhase.GLOBAL,
            clock = "21:42",
            matchLabel = "AL vs BLG · G1",
            headline = "经济领先易手 · BLG +1.8k",
            explanation = "本地视觉演示：测试全局事件卡，不代表真实经济数据。",
            evidence = listOf(
                TacticalHudEvidence("EVIDENCE", "VERIFIED_DELTA"),
                TacticalHudEvidence("WINDOW", "10s"),
                TacticalHudEvidence("LEAD", "+1.8k"),
            ),
            players = emptyList(),
            sourceLabel = "LOCAL PREVIEW · NOT FACT",
        ),
        TacticalHudPresentation(
            active = true,
            sourceMode = TacticalHudSourceMode.PREVIEW,
            phase = TacticalHudPhase.GLOBAL,
            clock = "27:20",
            matchLabel = "AL vs BLG · G1",
            headline = "男爵变化 · BLG +1",
            explanation = "本地视觉演示：目标模块只展示已拥有字段，不制造目标血量或位置。",
            evidence = listOf(
                TacticalHudEvidence("EVIDENCE", "VERIFIED_DELTA"),
                TacticalHudEvidence("OBJECTIVE", "男爵"),
                TacticalHudEvidence("WINDOW", "10s"),
            ),
            players = emptyList(),
            sourceLabel = "LOCAL PREVIEW · NOT FACT",
        ),
    )

    private val _state = MutableStateFlow(TacticalHudPreviewState(totalSteps = script.size))
    val state: StateFlow<TacticalHudPreviewState> = _state.asStateFlow()

    @Synchronized
    fun startAuto() {
        playbackJob?.cancel()
        _state.value = TacticalHudPreviewState(
            active = true,
            autoPlay = true,
            totalSteps = script.size,
            presentation = script.first(),
        )
        playbackJob = scope.launch {
            delay(700)
            while (_state.value.active && _state.value.autoPlay) {
                advanceInternal()
                if (_state.value.active && _state.value.autoPlay) delay(3_000)
            }
        }
    }

    @Synchronized
    fun startManual() {
        playbackJob?.cancel()
        _state.value = TacticalHudPreviewState(
            active = true,
            autoPlay = false,
            totalSteps = script.size,
            presentation = script.first(),
        )
    }

    @Synchronized
    fun next() {
        if (!_state.value.active) startManual()
        if (_state.value.autoPlay) {
            playbackJob?.cancel()
            _state.value = _state.value.copy(autoPlay = false)
        }
        advanceInternal()
    }

    @Synchronized
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _state.value = TacticalHudPreviewState(totalSteps = script.size)
    }

    @Synchronized
    private fun advanceInternal() {
        val current = _state.value
        if (!current.active) return
        val nextIndex = (current.step + 1).coerceAtMost(script.lastIndex)
        val finished = nextIndex >= script.lastIndex
        _state.value = current.copy(
            autoPlay = current.autoPlay && !finished,
            step = nextIndex,
            presentation = script[nextIndex],
        )
    }
}
