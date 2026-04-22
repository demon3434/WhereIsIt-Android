package com.whereisit.findthings

import com.google.common.truth.Truth.assertThat
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import com.whereisit.findthings.ui.voice.buildVoiceSearchCandidates
import com.whereisit.findthings.ui.voice.normalizeVoiceSearchQuery
import org.junit.Test

class VoiceSearchQueryNormalizerTest {
    @Test
    fun normalizeVoiceSearchQuery_removesLeadingFillerPhrase() {
        assertThat(normalizeVoiceSearchQuery("我要找鼠标")).isEqualTo("鼠标")
    }

    @Test
    fun normalizeVoiceSearchQuery_keepsActualItemName() {
        assertThat(normalizeVoiceSearchQuery("塑料魔剑")).isEqualTo("塑料魔剑")
    }

    @Test
    fun buildVoiceSearchCandidates_prioritizesNormalizedAndKeywordTokens() {
        val result = VoiceFinalizeResponse(
            finalText = "我要找罗技鼠标",
            normalizedQuery = "罗技 鼠标",
            keywords = listOf("鼠标")
        )

        assertThat(buildVoiceSearchCandidates(result))
            .containsAtLeast("罗技 鼠标", "罗技鼠标", "罗技", "鼠标")
            .inOrder()
    }
}
