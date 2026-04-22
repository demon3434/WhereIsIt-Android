package com.whereisit.findthings.data.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.whereisit.findthings.data.repository.AppError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

interface VoiceRecorder {
    fun start(onAudioFrame: (ByteArray) -> Unit)
    fun stop()
}

class AudioRecordVoiceRecorder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val channels: Int = CHANNELS,
    private val frameDurationMs: Int = FRAME_DURATION_MS
) : VoiceRecorder {
    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null

    override fun start(onAudioFrame: (ByteArray) -> Unit) {
        if (isRunning.getAndSet(true)) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val frameBytes = sampleRate * channels * BYTES_PER_SAMPLE * frameDurationMs / 1000
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (minBufferSize <= 0) {
            isRunning.set(false)
            throw AppError.Business("录音初始化失败，请稍后重试")
        }

        val resolvedBufferSize = maxOf(minBufferSize, frameBytes * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioEncoding,
            resolvedBufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            isRunning.set(false)
            throw AppError.Business("无法访问麦克风，请检查权限或占用情况")
        }

        audioRecord = recorder
        recorder.startRecording()
        workerThread = thread(start = true, name = "voice-recorder") {
            val buffer = ByteArray(frameBytes)
            try {
                while (isRunning.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    onAudioFrame(buffer.copyOf(read))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    override fun stop() {
        if (!isRunning.getAndSet(false)) return
        workerThread?.join(300)
        workerThread = null
        audioRecord = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BYTES_PER_SAMPLE = 2
        const val FRAME_DURATION_MS = 100
    }
}
