package com.whereisit.findthings.data.voice.model

import com.google.gson.annotations.SerializedName
import com.whereisit.findthings.data.model.ItemDto

data class Envelope<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class VoiceFinalizeResponse(
    @SerializedName(value = "session_id", alternate = ["sessionId"])
    val sessionId: String? = null,
    @SerializedName(value = "first_stage_text", alternate = ["firstStageText"])
    val firstStageText: String? = null,
    @SerializedName(value = "final_text", alternate = ["finalText", "recognizedText"])
    val finalText: String = "",
    @SerializedName(value = "normalized_query", alternate = ["normalizedQuery", "normalizedText"])
    val normalizedQuery: String? = null,
    val keywords: List<String> = emptyList(),
    val items: List<ItemDto> = emptyList(),
    val timing: VoiceSearchTiming? = null
)

data class VoiceSearchTiming(
    @SerializedName(value = "offline_asr_ms", alternate = ["offlineAsrMs"])
    val offlineAsrMs: Int? = null,
    @SerializedName(value = "search_ms", alternate = ["searchMs"])
    val searchMs: Int? = null
)

sealed interface VoiceWsMessage {
    data class Session(val sessionId: String) : VoiceWsMessage
    data class Partial(val text: String) : VoiceWsMessage
    data class Finalizing(val text: String?) : VoiceWsMessage
    data class Error(val message: String) : VoiceWsMessage
    data class Unknown(val raw: String) : VoiceWsMessage
}
