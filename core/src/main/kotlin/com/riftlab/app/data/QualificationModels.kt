package com.riftlab.app.data

/**
 * dev.70 qualification model.
 *
 * Tournament standings, annual Championship Points and qualification routes are three different
 * concepts. This layer keeps them separate and carries evidence/source on every route node so an
 * official confirmation can never be confused with a RiftLab-derived possibility.
 */
enum class QualificationTeamState(val label: String) {
    LOCKED("已锁定"),
    CONTENDING("仍可争夺"),
    ELIMINATED("已淘汰"),
    PENDING("待确认")
}

enum class QualificationEvidence(val label: String) {
    OFFICIAL("官方确认"),
    PROVIDER("Provider 数据"),
    DERIVED("RiftLab 推导"),
    PENDING("等待可信来源")
}

enum class QualificationNodeState(val label: String) {
    CONFIRMED("已确认"),
    AVAILABLE("可继续争夺"),
    BLOCKED("已关闭"),
    PENDING("待确认")
}

enum class QualificationMechanism(val label: String) {
    CHAMPIONSHIP_POINTS("Championship Points"),
    DIRECT_PLACEMENT("联赛 / 季后赛名次直通"),
    REGIONAL_QUALIFIER("区域资格赛 / 附加赛"),
    PARTICIPANT_ORIGIN("国际赛参赛资格来源"),
    MIXED("混合资格体系"),
    UNKNOWN("资格规则待确认")
}

enum class QualificationSegmentType(val label: String) {
    DIRECT_QUALIFICATION("名次 / 冠军直通"),
    CHAMPIONSHIP_POINTS("Championship Points"),
    REGIONAL_QUALIFIER("区域资格赛 / 附加赛"),
    PARTICIPANT_ORIGIN("国际赛参赛来源")
}

data class QualificationMechanismSegment(
    val type: QualificationSegmentType,
    val detail: String,
    val source: String,
    val evidence: QualificationEvidence
)

data class QualificationRouteNode(
    val id: String,
    val label: String,
    val detail: String,
    val state: QualificationNodeState,
    val source: String,
    val evidence: QualificationEvidence
)

data class QualificationRuleRecord(
    val title: String,
    val detail: String,
    val source: String,
    val evidence: QualificationEvidence
)

data class TeamQualificationRoute(
    val tournamentId: String,
    val teamId: String,
    val teamCode: String,
    val targetEvent: String,
    val status: QualificationTeamState,
    val championshipPoints: Int? = null,
    val leagueStandingPoints: Int? = null,
    val annualPointBreakdown: String = "",
    val route: List<QualificationRouteNode> = emptyList(),
    val source: String = "",
    val evidence: QualificationEvidence = QualificationEvidence.PENDING,
    val updatedThrough: String = ""
)

data class QualificationTournamentSnapshot(
    val tournamentId: String,
    val title: String,
    val targetEvent: String,
    val routes: List<TeamQualificationRoute> = emptyList(),
    val segments: List<QualificationMechanismSegment> = emptyList(),
    val rules: List<QualificationRuleRecord> = emptyList(),
    val sourceSummary: String = "",
    val note: String = "",
    val mechanism: QualificationMechanism = QualificationMechanism.UNKNOWN,
    val mechanismEvidence: QualificationEvidence = QualificationEvidence.PENDING,
    val mechanismDetail: String = ""
)

data class QualificationCenterState(
    val snapshotsByTournamentId: Map<String, QualificationTournamentSnapshot> = emptyMap(),
    val selectedTournamentId: String = "",
    val selectedTeamCode: String = "",
    val lastRefreshEpochMs: Long = 0L,
    val statusMessage: String = "资格路径中心尚未同步"
) {
    val selected: QualificationTournamentSnapshot?
        get() = snapshotsByTournamentId[selectedTournamentId]

    val selectedRoute: TeamQualificationRoute?
        get() = selected?.routes?.firstOrNull { it.teamCode.equals(selectedTeamCode, ignoreCase = true) }
            ?: selected?.routes?.firstOrNull()
}
