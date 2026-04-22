package com.whereisit.findthings.data.voice

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.whereisit.findthings.data.network.ApiFactory
import com.whereisit.findthings.data.repository.AppError
import com.whereisit.findthings.data.repository.SessionRepository
import com.whereisit.findthings.data.voice.model.Envelope
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import com.whereisit.findthings.data.voice.model.VoiceWsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException

class VoiceSearchRepository(
    appContext: Context,
    private val apiFactory: ApiFactory,
    private val sessionRepository: SessionRepository,
    private val gson: Gson = Gson()
) {
    private val appContext = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val partialText = MutableStateFlow("")
    private var recorder: VoiceRecorder? = null
    private var wsClient: VoiceSearchWebSocketClient? = null
    private var wsMessagesJob: Job? = null
    private var senderJob: Job? = null
    private var audioChannel: Channel<ByteArray>? = null
    private var sessionId: String? = null

    fun partialTextFlow(): StateFlow<String> = partialText.asStateFlow()

    suspend fun startListening() {
        cleanup()

        val auth = authInfo()
        partialText.value = ""
        sessionId = null

        val client = OkHttpVoiceSearchWebSocketClient(
            url = buildVoiceWsUrl(auth.baseUrl),
            authToken = auth.token
        )
        wsClient = client
        client.connect()

        wsMessagesJob = scope.launch {
            client.observeMessages().collectLatest { message ->
                when (message) {
                    is VoiceWsMessage.Session -> sessionId = message.sessionId.ifBlank { null }
                    is VoiceWsMessage.Partial -> {
                        val text = message.text.trim()
                        if (text.isNotEmpty() && text != partialText.value) {
                            partialText.value = text
                        }
                    }
                    is VoiceWsMessage.Finalizing -> {
                        message.text?.trim()?.takeIf { it.isNotEmpty() }?.let { partialText.value = it }
                    }
                    is VoiceWsMessage.Error -> throw AppError.Business(message.message)
                    is VoiceWsMessage.Unknown -> Unit
                }
            }
        }

        client.sendStart()
        withTimeout(1_500) {
            while (sessionId.isNullOrBlank()) {
                delay(20)
            }
        }

        val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
        audioChannel = channel
        senderJob = scope.launch {
            var seq = 1
            for (frame in channel) {
                client.sendAudio(seq = seq++, pcm = frame)
            }
        }

        val voiceRecorder = AudioRecordVoiceRecorder()
        recorder = voiceRecorder
        voiceRecorder.start { frame ->
            channel.trySend(frame.copyOf())
        }
    }

    suspend fun stopAndFinalize(): VoiceFinalizeResponse {
        return guard {
            val currentClient = wsClient ?: throw AppError.Business("实时识别尚未开始")
            recorder?.stop()
            recorder = null

            audioChannel?.close()
            senderJob?.join()
            currentClient.sendStop()

            val currentSessionId = withTimeout(2_000) {
                while (sessionId.isNullOrBlank()) {
                    delay(20)
                }
                sessionId!!
            }
            val firstStageText = awaitStablePartialText()
            val auth = authInfo()
            val raw = apiFactory.service(auth.baseUrl).finalizeVoiceSearch(
                auth = "Bearer ${auth.token}",
                sessionId = currentSessionId.toRequestBody("text/plain".toMediaType()),
                firstStageText = firstStageText.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
            )
            parseFinalizeResponse(raw)
        }.also {
            cleanup()
        }
    }

    suspend fun cancel() {
        cleanup()
    }

    private suspend fun cleanup() {
        recorder?.stop()
        recorder = null

        audioChannel?.close()
        audioChannel = null

        senderJob?.cancelAndJoin()
        senderJob = null

        wsMessagesJob?.cancelAndJoin()
        wsMessagesJob = null

        wsClient?.close()
        wsClient = null

        partialText.value = ""
        sessionId = null
    }

    private suspend fun authInfo(): VoiceAuthInfo {
        val settings = sessionRepository.current()
        val baseUrl = settings.activeBaseUrl()
        if (baseUrl.isBlank()) throw AppError.Validation("请先配置服务器地址")
        if (settings.token.isBlank()) throw AppError.Unauthorized()
        return VoiceAuthInfo(baseUrl = baseUrl, token = settings.token)
    }

    private suspend fun awaitStablePartialText(
        stabilityWindowMs: Long = 350,
        pollIntervalMs: Long = 50
    ): String {
        var latest = partialText.value.trim()
        var stableForMs = 0L

        while (stableForMs < stabilityWindowMs) {
            delay(pollIntervalMs)
            val current = partialText.value.trim()
            if (current != latest) {
                latest = current
                stableForMs = 0L
            } else {
                stableForMs += pollIntervalMs
            }
        }

        return latest
    }

    private fun parseFinalizeResponse(raw: JsonElement): VoiceFinalizeResponse {
        val payload = unwrapEnvelope(raw)
        return gson.fromJson(payload, VoiceFinalizeResponse::class.java)
    }

    private fun unwrapEnvelope(raw: JsonElement): JsonElement {
        if (!raw.isJsonObject) return raw
        val obj = raw.asJsonObject
        val hasEnvelopeFields = obj.has("code") && obj.has("message") && obj.has("data")
        if (!hasEnvelopeFields) return raw

        val type = object : TypeToken<Envelope<JsonElement>>() {}.type
        val envelope: Envelope<JsonElement> = gson.fromJson(raw, type)
        if (envelope.code != 0 || envelope.data == null || envelope.data == JsonNull.INSTANCE) {
            throw AppError.Business(envelope.message.ifBlank { "语音搜索失败" })
        }
        return envelope.data
    }

    private suspend fun <T> guard(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: AppError) {
            throw e
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val message = parseHttpErrorMessage(body)
            throw if (e.code() == 401) AppError.Unauthorized(message) else AppError.Business(message)
        } catch (_: IOException) {
            throw AppError.Network()
        } catch (e: Exception) {
            throw AppError.Unknown(e.message ?: "语音搜索失败")
        }
    }

    private fun parseHttpErrorMessage(body: String): String {
        if (body.isBlank()) return "语音搜索请求失败"
        val element = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return body
        if (!element.isJsonObject) return body
        val obj = element.asJsonObject
        return obj.stringOrNull("message")
            ?: obj.stringOrNull("detail")
            ?: body
    }
}

internal fun buildVoiceWsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().removeSuffix("/")
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> {
            "wss://${trimmed.removePrefix("https://")}/api/voice-search/stream"
        }
        trimmed.startsWith("http://", ignoreCase = true) -> {
            "ws://${trimmed.removePrefix("http://")}/api/voice-search/stream"
        }
        else -> "$trimmed/api/voice-search/stream"
    }
}

private fun JsonObject.stringOrNull(name: String): String? {
    return runCatching { get(name)?.asString }.getOrNull()?.takeIf { it.isNotBlank() }
}

private data class VoiceAuthInfo(
    val baseUrl: String,
    val token: String
)
