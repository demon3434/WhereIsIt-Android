package com.whereisit.findthings.ui.voice

import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import java.util.Locale

private val LEADING_FILLER_PATTERNS = listOf(
    Regex("^(?:请)?(?:帮我|替我|给我)?(?:找|搜|搜索|查找|查|查询)(?:一下)?"),
    Regex("^我(?:想|想要|要)?(?:找|搜|搜索|查找|查|查询)(?:一下)?"),
    Regex("^有没有"),
    Regex("^看看有没有"),
    Regex("^想找"),
    Regex("^我要")
)

private val TRAILING_FILLER_PATTERNS = listOf(
    Regex("(?:放在哪里|放在哪儿|放在哪|在哪里|在哪儿|在哪|有吗|有没有|吗|呢)+$"),
    Regex("(?:帮我|给我|一下)+$")
)

internal fun buildVoiceSearchCandidates(result: VoiceFinalizeResponse): List<String> {
    val orderedSources = buildList {
        add(result.normalizedQuery)
        addAll(result.keywords)
        add(result.finalText)
        add(result.firstStageText)
    }

    val seen = linkedSetOf<String>()
    val candidates = mutableListOf<String>()
    for (source in orderedSources) {
        val normalized = normalizeVoiceSearchQuery(source)
        if (normalized.isBlank()) continue

        addCandidate(candidates, seen, normalized)
        addCandidate(candidates, seen, normalized.replace(" ", ""))

        normalized
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .forEach { addCandidate(candidates, seen, it) }
    }
    return candidates
}

internal fun normalizeVoiceSearchQuery(raw: String?): String {
    if (raw.isNullOrBlank()) return ""

    var value = raw.trim()
        .replace(Regex("[\\p{Punct}，。！？、；：“”‘’（）《》【】]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    var changed = true
    while (changed && value.isNotBlank()) {
        changed = false
        for (pattern in LEADING_FILLER_PATTERNS) {
            val updated = value.replace(pattern, "").trim()
            if (updated != value) {
                value = updated
                changed = true
            }
        }
    }

    for (pattern in TRAILING_FILLER_PATTERNS) {
        value = value.replace(pattern, "").trim()
    }

    return value
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun addCandidate(target: MutableList<String>, seen: MutableSet<String>, candidate: String) {
    val normalized = candidate.trim()
    if (normalized.isBlank()) return
    val key = normalized.lowercase(Locale.ROOT)
    if (seen.add(key)) {
        target += normalized
    }
}
