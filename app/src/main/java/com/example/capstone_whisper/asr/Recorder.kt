package com.example.cpastone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/**
 * AudioRecord 기반 PCM 녹음기.
 * RECORD_AUDIO 권한을 사전에 확인하고, 권한 부족 시 SecurityException을 던집니다.
 */
class Recorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val pcmStream = ByteArrayOutputStream()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )

    /**
     * 녹음 시작 전 RECORD_AUDIO 권한 확인
     * 권한 없으면 SecurityException 발생
     */
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            ).apply { startRecording() }
        } catch (e: SecurityException) {
            // 권한 거부 처리
            recorder = null
            throw e
        }

        isRecording = true
        pcmStream.reset()

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Little-endian PCM16 저장
                    for (i in 0 until read) {
                        val sample = buffer[i]
                        pcmStream.write(sample.toInt() and 0xFF)
                        pcmStream.write(sample.toInt().shr(8) and 0xFF)
                    }
                }
            }
        }.start()
    }

    /** 녹음 중지 */
    fun stopRecording() {
        isRecording = false
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    /**
     * 녹음된 PCM 데이터를 FloatArray로 변환
     */
    fun getRawData():ByteArrayOutputStream{
        return pcmStream
    }
    fun getPcmData(): FloatArray {
        val bytes = pcmStream.toByteArray()
        val sampleCount = bytes.size / 2
        return FloatArray(sampleCount) { i ->
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            ((hi shl 8) or lo) / 32768.0f
        }
    }
}

