package com.whereisit.findthings.data.voice

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.whereisit.findthings.data.voice.model.VoiceWsMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface VoiceSearchWebSocketClient {
    suspend fun connect()
    suspend fun sendStart(sampleRate: Int = 16_000, channels: Int = 1, encoding: String = "pcm_s16le")
    suspend fun sendAudio(seq: Int, pcm: ByteArray)
    suspend fun sendStop()
    fun observeMessages(): Flow<VoiceWsMessage>
    suspend fun close()
}

class OkHttpVoiceSearchWebSocketClient(
    private val url: String,
    private val authToken: String,
    private val gson: Gson = Gson()
) : VoiceSearchWebSocketClient {
    private val messages = MutableSharedFlow<VoiceWsMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val client = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null

    override suspend fun connect() {
        if (webSocket != null) return
        suspendCancellableCoroutine<Unit> { continuation ->
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $authToken")
                .build()

            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@OkHttpVoiceSearchWebSocketClient.webSocket = webSocket
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    messages.tryEmit(parseMessage(text))
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    messages.tryEmit(VoiceWsMessage.Unknown(bytes.utf8()))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    messages.tryEmit(VoiceWsMessage.Error(t.message ?: "实时识别连接失败"))
                    if (continuation.isActive) continuation.resumeWithException(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    this@OkHttpVoiceSearchWebSocketClient.webSocket = null
                }
            })

            continuation.invokeOnCancellation { socket.cancel() }
        }
    }

    override suspend fun sendStart(sampleRate: Int, channels: Int, encoding: String) {
        val payload = JsonObject().apply {
            addProperty("type", "start")
            addProperty("sample_rate", sampleRate)
            addProperty("channels", channels)
            addProperty("encoding", encoding)
        }
        sendJson(payload)
    }

    override suspend fun sendAudio(seq: Int, pcm: ByteArray) {
        val payload = JsonObject().apply {
            addProperty("type", "audio")
            addProperty("seq", seq)
            addProperty("data", Base64.getEncoder().encodeToString(pcm))
        }
        sendJson(payload)
    }

    override suspend fun sendStop() {
        val payload = JsonObject().apply {
            addProperty("type", "stop")
        }
        sendJson(payload)
    }

    override fun observeMessages(): Flow<VoiceWsMessage> = messages.asSharedFlow()

    override suspend fun close() {
        webSocket?.close(1000, "client closed")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun sendJson(payload: JsonObject) {
        val sent = webSocket?.send(gson.toJson(payload)) ?: false
        if (!sent) {
            throw IllegalStateException("voice websocket is not connected")
        }
    }

    private fun parseMessage(text: String): VoiceWsMessage {
        return runCatching {
            val json = JsonParser.parseString(text).asJsonObject
            when (json.get("type")?.asString.orEmpty()) {
                "session" -> VoiceWsMessage.Session(json.get("session_id")?.asString.orEmpty())
                "partial" -> VoiceWsMessage.Partial(json.get("text")?.asString.orEmpty().trim())
                "finalizing" -> VoiceWsMessage.Finalizing(json.get("text")?.asString)
                "error" -> VoiceWsMessage.Error(json.get("message")?.asString ?: "实时识别失败")
                else -> VoiceWsMessage.Unknown(text)
            }
        }.getOrElse {
            VoiceWsMessage.Unknown(text)
        }
    }
}
