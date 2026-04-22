package com.whereisit.findthings.ui.voice

sealed interface VoiceSearchUiState {
    data object Idle : VoiceSearchUiState
    data class Listening(val partialText: String) : VoiceSearchUiState
    data class Finalizing(val lastText: String) : VoiceSearchUiState
    data class Error(val message: String, val lastText: String?) : VoiceSearchUiState
}
