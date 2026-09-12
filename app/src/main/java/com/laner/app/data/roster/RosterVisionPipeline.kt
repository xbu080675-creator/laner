package com.laner.app.data.roster

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class RosterOcrToken(
    val text: String,
    val engine: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val normalizedCenterX: Float get() = if (imageWidth > 0) ((left + right) / 2f) / imageWidth else 0.5f
}

data class RosterOcrResult(
    val text: String,
    val engines: List<String>,
    val lineCount: Int,
    val tokens: List<RosterOcrToken>,
    val imageWidth: Int,
    val imageHeight: Int,
)

internal object RosterVisionPipeline {
    suspend fun recognize(bitmap: Bitmap, leagueHint: String): RosterOcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizers = recognizers(leagueHint)
        val flatLines = linkedSetOf<String>()
        val tokens = mutableListOf<RosterOcrToken>()
        val engines = mutableListOf<String>()
        try {
            recognizers.forEach { (label, recognizer) ->
                val result = recognizer.process(image).awaitTask()
                engines += label
                result.textBlocks.flatMap { it.lines }.forEach { line ->
                    line.text.trim().takeIf { it.isNotBlank() }?.let(flatLines::add)
                    line.elements.forEach { element ->
                        val token = element.text.trim()
                        val box = element.boundingBox
                        if (token.isBlank() || box == null) return@forEach
                        tokens += RosterOcrToken(
                            text = token,
                            engine = label,
                            left = box.left.coerceAtLeast(0),
                            top = box.top.coerceAtLeast(0),
                            right = box.right.coerceAtMost(bitmap.width),
                            bottom = box.bottom.coerceAtMost(bitmap.height),
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                        )
                    }
                }
            }
        } finally {
            recognizers.forEach { (_, recognizer) -> runCatching { recognizer.close() } }
        }
        return RosterOcrResult(
            text = flatLines.joinToString("\n"),
            engines = engines,
            lineCount = flatLines.size,
            tokens = tokens.distinctBy {
                listOf(it.engine, it.text.lowercase(), it.left / 8, it.top / 8, it.right / 8, it.bottom / 8).joinToString(":")
            }.sortedWith(compareBy<RosterOcrToken> { it.top }.thenBy { it.left }),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        )
    }

    private fun recognizers(leagueHint: String): List<Pair<String, TextRecognizer>> {
        val hint = leagueHint.uppercase()
        return buildList {
            add("latin" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS))
            when {
                "LPL" in hint || "LCP" in hint || "PCS" in hint -> add(
                    "chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                )
                "LCK" in hint -> add(
                    "korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                )
                "LJL" in hint -> add(
                    "japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                )
            }
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}

internal data class RosterCandidateExtraction(
    val merged: Map<String, List<String>>,
    val left: Map<String, List<String>>,
    val right: Map<String, List<String>>,
    val layoutMode: String,
)

internal object RosterCandidateExtractor {
    private val roles = listOf("TOP", "JUG", "MID", "BOT", "SUP")
    private val aliases = mapOf(
        "TOP" to listOf("TOP", "上单", "탑", "トップ"),
        "JUG" to listOf("JUG", "JGL", "JUNGLE", "打野", "정글", "ジャングル"),
        "MID" to listOf("MID", "中单", "미드", "ミッド"),
        "BOT" to listOf("BOT", "ADC", "BOTTOM", "下路", "원딜", "ボット"),
        "SUP" to listOf("SUP", "SUPPORT", "辅助", "서폿", "サポート"),
    )
    private val noise = setOf(
        "TOP", "JUG", "JGL", "JUNGLE", "MID", "BOT", "ADC", "BOTTOM", "SUP", "SUPPORT",
        "LPL", "LCK", "LEC", "LCS", "LCP", "LOL", "ROSTER", "STARTING", "LINEUP", "ESPORTS",
        "GAMING", "GAME", "MATCH", "VS", "BO3", "BO5", "NULL",
    )

    fun extract(result: RosterOcrResult): RosterCandidateExtraction {
        val flat = mutableRoleMap()
        result.text.lineSequence().forEach { line ->
            val role = roleFor(line) ?: return@forEach
            Regex("[A-Za-z][A-Za-z0-9._-]{1,23}").findAll(line)
                .mapNotNull { playerIdOrNull(it.value) }
                .forEach { flat.getValue(role).add(it) }
        }

        val left = mutableRoleMap()
        val right = mutableRoleMap()
        val anchors = result.tokens.mapNotNull { token -> roleFor(token.text)?.let { it to token } }
        val players = result.tokens.filter { token ->
            token.engine == "latin" && roleFor(token.text) == null && playerIdOrNull(token.text) != null
        }
        anchors.forEach { (role, anchor) ->
            val anchorHeight = (anchor.bottom - anchor.top).coerceAtLeast(12)
            val yTolerance = maxOf(30f, anchorHeight * 2.8f)
            players.asSequence()
                .filter { kotlin.math.abs(it.centerY - anchor.centerY) <= yTolerance }
                .sortedBy { kotlin.math.abs(it.centerY - anchor.centerY) }
                .take(10)
                .forEach { token ->
                    val player = playerIdOrNull(token.text) ?: return@forEach
                    when {
                        token.normalizedCenterX < 0.47f -> left.getValue(role).add(player)
                        token.normalizedCenterX > 0.53f -> right.getValue(role).add(player)
                    }
                }
        }

        val normalizedFlat = normalize(flat)
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        val merged = normalize(
            roles.associateWith { role ->
                (normalizedFlat[role].orEmpty() + normalizedLeft[role].orEmpty() + normalizedRight[role].orEmpty()).toMutableList()
            }
        )
        val leftCount = normalizedLeft.count { it.value.isNotEmpty() }
        val rightCount = normalizedRight.count { it.value.isNotEmpty() }
        val mode = when {
            leftCount >= 3 && rightCount >= 3 -> "TWO_COLUMN"
            leftCount >= 3 -> "LEFT_COLUMN"
            rightCount >= 3 -> "RIGHT_COLUMN"
            anchors.isNotEmpty() -> "GEOMETRY_PARTIAL"
            else -> "TEXT_ONLY"
        }
        return RosterCandidateExtraction(merged, normalizedLeft, normalizedRight, mode)
    }

    private fun mutableRoleMap() = linkedMapOf(
        "TOP" to mutableListOf<String>(),
        "JUG" to mutableListOf<String>(),
        "MID" to mutableListOf<String>(),
        "BOT" to mutableListOf<String>(),
        "SUP" to mutableListOf<String>(),
    )

    private fun normalize(map: Map<String, List<String>>): Map<String, List<String>> =
        roles.associateWith { role -> map[role].orEmpty().distinctBy { it.lowercase() }.take(6) }

    private fun roleFor(raw: String): String? {
        val normalized = raw.uppercase()
        return roles.firstOrNull { role -> aliases.getValue(role).any { normalized.contains(it) } }
    }

    private fun playerIdOrNull(raw: String): String? {
        val value = raw.trim().trim('(', ')', '[', ']', '{', '}', ':', ';', ',', '.')
        if (!value.matches(Regex("[A-Za-z][A-Za-z0-9._-]{1,23}"))) return null
        if (value.uppercase() in noise || value.length < 2) return null
        return value
    }
}
