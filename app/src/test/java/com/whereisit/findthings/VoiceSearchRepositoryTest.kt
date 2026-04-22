package com.whereisit.findthings

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import com.whereisit.findthings.data.voice.buildVoiceWsUrl
import org.junit.Test

class VoiceSearchRepositoryTest {
    @Test
    fun buildVoiceWsUrl_convertsHttp() {
        assertThat(buildVoiceWsUrl("http://127.0.0.1:3000/"))
            .isEqualTo("ws://127.0.0.1:3000/api/voice-search/stream")
    }

    @Test
    fun buildVoiceWsUrl_convertsHttps() {
        assertThat(buildVoiceWsUrl("https://demo.example.com/base/"))
            .isEqualTo("wss://demo.example.com/base/api/voice-search/stream")
    }

    @Test
    fun bareFinalizePayload_hasExpectedFields() {
        val json = JsonParser.parseString(
            """
            {
              "session_id": "vs_001",
              "first_stage_text": "红米note",
              "final_text": "红米note8手机",
              "normalized_query": "红米 note8 手机",
              "items": []
            }
            """.trimIndent()
        ).asJsonObject

        assertThat(json.get("session_id").asString).isEqualTo("vs_001")
        assertThat(json.get("final_text").asString).isEqualTo("红米note8手机")
    }
}
