package com.laner.app.data.qualification

import com.laner.core.application.ProviderQualificationSnapshot
import com.laner.core.application.ProviderRead
import com.laner.core.application.QualificationSourcePort
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.QualificationEvidenceKind
import com.laner.core.domain.QualificationMechanism
import com.laner.core.domain.TournamentEdition

/**
 * Narrow official-rule seed for 2026 final regional splits.
 *
 * This source publishes only qualification mechanisms explicitly stated by the Riot 2026 League
 * Handbook. It does not publish team-level LOCKED/ELIMINATED states and it does not fabricate
 * Championship Point totals from standings.
 */
class Official2026QualificationSource : QualificationSourcePort {
    override val providerId: String = "riot-handbook-2026"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readQualification(
        edition: TournamentEdition,
        context: SourceRequestContext,
    ): ProviderRead<ProviderQualificationSnapshot> {
        val rule = ruleFor(edition)
        return ProviderRead.Success(
            ProviderQualificationSnapshot(
                title = "${edition.displayName} · 官方资格机制",
                targetEventName = if (edition.seasonYear == 2026) "2026 全球总决赛" else "资格目标待确认",
                mechanism = rule?.mechanism ?: QualificationMechanism.UNKNOWN,
                mechanismEvidence = if (rule == null) QualificationEvidenceKind.PENDING else QualificationEvidenceKind.OFFICIAL,
                routes = emptyList(),
                note = rule?.detail
                    ?: "当前届次没有被本地已核实的官方资格规则覆盖；不从 Standings、参赛名单或赛事名称自行推断晋级机制。",
                observedAtEpochMillis = context.nowEpochMillis,
                sourceUri = rule?.sourceUri,
            )
        )
    }

    private fun ruleFor(edition: TournamentEdition): VerifiedRule? {
        if (edition.seasonYear != 2026 || !isFinalRegionalSplit(edition)) return null
        return when (leagueToken(edition)) {
            "lpl" -> VerifiedRule(
                mechanism = QualificationMechanism.MIXED,
                detail = "Riot 2026 League Handbook：LPL 第三赛段冠军直通 Worlds；Championship Points 前列进入区域资格赛，竞争其余 Worlds 席位。这里只确认机制，不据当前 Standings 猜队伍资格状态。",
                sourceUri = "$SPLIT3_HANDBOOK/league/$LPL_ID",
            )
            "lck" -> VerifiedRule(
                mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                detail = "Riot 2026 League Handbook：LCK Split 3 Playoffs 前三名晋级 Worlds。队伍是否已经锁定资格仍需正式赛果证明。",
                sourceUri = "$SPLIT3_HANDBOOK/league/$LCK_ID",
            )
            "lcp" -> VerifiedRule(
                mechanism = QualificationMechanism.MIXED,
                detail = "Riot 2026 League Handbook：LCP Playoffs 前两名晋级 Worlds，第三个 Worlds 名额通过 Championship Points 决定。",
                sourceUri = "$SPLIT3_HANDBOOK/league/$LCP_ID",
            )
            "lcs" -> VerifiedRule(
                mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                detail = "Riot 2026 League Handbook：LCS Split 3 Playoffs 前三名晋级 Worlds。",
                sourceUri = "$SPLIT3_HANDBOOK/league/$LCS_ID",
            )
            "cblol" -> VerifiedRule(
                mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                detail = "Riot 2026 League Handbook：CBLOL Split 3 冠军晋级 Worlds。",
                sourceUri = "$SPLIT3_HANDBOOK/league/$CBLOL_ID",
            )
            // The official LEC surface currently contains an internal inconsistency: its overview
            // marks 1st/2nd/3rd as Worlds qualifiers while the Playoffs prose says top two.
            // Keep it PENDING until Riot exposes a non-conflicting rule statement.
            "lec" -> null
            else -> null
        }
    }

    private fun isFinalRegionalSplit(edition: TournamentEdition): Boolean {
        val token = "${edition.slug} ${edition.stageName} ${edition.displayName}".lowercase()
        return token.contains("split-3") || token.contains("split 3") ||
            token.contains("summer") || edition.startEpochMillis >= JULY_1_2026_UTC
    }

    private fun leagueToken(edition: TournamentEdition): String {
        val id = edition.competition.id.value.substringAfterLast(':').lowercase()
        val name = edition.competition.name.lowercase()
        return when {
            id == "lpl" || name.contains("lpl") -> "lpl"
            id == "lck" || name.contains("lck") -> "lck"
            id == "lcp" || name.contains("lcp") -> "lcp"
            id == "lcs" || name.contains("lcs") -> "lcs"
            id == "cblol" || name.contains("cblol") -> "cblol"
            id == "lec" || name.contains("lec") -> "lec"
            else -> id
        }
    }

    private data class VerifiedRule(
        val mechanism: QualificationMechanism,
        val detail: String,
        val sourceUri: String,
    )

    private companion object {
        const val SPLIT3_HANDBOOK = "https://lolesports.com/en-US/season/115547545029543948/handbook/115548016979679447"
        const val LPL_ID = "98767991314006698"
        const val LCK_ID = "98767991310872058"
        const val LEC_ID = "98767991302996019"
        const val LCS_ID = "98767991299243165"
        const val CBLOL_ID = "98767991332355509"
        const val LCP_ID = "113476371197627891"
        const val JULY_1_2026_UTC = 1_783_036_800_000L
    }
}
