package com.whereisit.findthings.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whereisit.findthings.data.repository.AppError
import com.whereisit.findthings.data.voice.VoiceSearchRepository
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceSearchViewModel(
    private val repository: VoiceSearchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<VoiceSearchUiState>(VoiceSearchUiState.Idle)
    val uiState: StateFlow<VoiceSearchUiState> = _uiState.asStateFlow()

    private val _completedResult = MutableSharedFlow<VoiceFinalizeResponse>(extraBufferCapacity = 1)
    val completedResult = _completedResult.asSharedFlow()

    private var listeningJobStarted = false
    private var partialCollectorJob: Job? = null

    fun startListening() {
        if (listeningJobStarted || _uiState.value is VoiceSearchUiState.Finalizing) return
        listeningJobStarted = true
        _uiState.value = VoiceSearchUiState.Listening(partialText = "")

        viewModelScope.launch {
            try {
                repository.startListening()
                partialCollectorJob?.cancel()
                partialCollectorJob = launch {
                    repository.partialTextFlow().collect { text ->
                        when (val current = _uiState.value) {
                            is VoiceSearchUiState.Finalizing -> {
                                val updated = text.trim()
                                if (updated.isNotEmpty() && updated != current.lastText) {
                                    _uiState.value = VoiceSearchUiState.Finalizing(updated)
                                }
                            }

                            is VoiceSearchUiState.Listening -> {
                                if (text != current.partialText) {
                                    _uiState.value = VoiceSearchUiState.Listening(text)
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            } catch (e: AppError) {
                listeningJobStarted = false
                partialCollectorJob?.cancel()
                _uiState.value = VoiceSearchUiState.Error(e.message ?: "实时识别失败，请重试", null)
            }
        }
    }

    fun stopAndFinalize() {
        if (!listeningJobStarted) return
        listeningJobStarted = false
        val lastText = currentText()
        _uiState.value = VoiceSearchUiState.Finalizing(lastText)

        viewModelScope.launch {
            try {
                val result = repository.stopAndFinalize()
                partialCollectorJob?.cancel()
                _completedResult.tryEmit(result)
                _uiState.value = VoiceSearchUiState.Idle
            } catch (e: AppError) {
                partialCollectorJob?.cancel()
                _uiState.value = VoiceSearchUiState.Error(e.message ?: "语音识别失败，请重试", lastText.takeIf { it.isNotBlank() })
            }
        }
    }

    fun cancelListening() {
        listeningJobStarted = false
        partialCollectorJob?.cancel()
        viewModelScope.launch {
            repository.cancel()
            _uiState.value = VoiceSearchUiState.Idle
        }
    }

    fun reset() {
        partialCollectorJob?.cancel()
        _uiState.value = VoiceSearchUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        partialCollectorJob?.cancel()
        viewModelScope.launch {
            repository.cancel()
        }
    }

    private fun currentText(): String {
        return when (val state = _uiState.value) {
            is VoiceSearchUiState.Listening -> state.partialText
            is VoiceSearchUiState.Finalizing -> state.lastText
            is VoiceSearchUiState.Error -> state.lastText.orEmpty()
            VoiceSearchUiState.Idle -> ""
        }
    }

    companion object {
        fun factory(repository: VoiceSearchRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceSearchViewModel(repository) as T
            }
        }
    }
}
