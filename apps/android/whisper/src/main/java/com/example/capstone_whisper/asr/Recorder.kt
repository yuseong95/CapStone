package com.example.capstone_whisper.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.*

class Recorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val pcmStream = ByteArrayOutputStream()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
        channelConfig,
        audioFormat
    )

    private var readCallback: ((ShortArray, Int) -> Unit)? = null

    /**
     * 녹음 시작 (권한 확인 포함)
     */
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context,
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
            recorder = null
            throw e
        }

        isRecording = true
        pcmStream.reset()

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    for (i in 0 until read) {
                        val sample = buffer[i]
                        pcmStream.write(sample.toInt() and 0xFF)
                        pcmStream.write(sample.toInt().shr(8) and 0xFF)
                    }
                    readCallback?.invoke(buffer, read)
                }

                delay(20)
            }
        }

    }

    /**
     * 녹음 중지
     */
    fun stopRecording() {
        Log.d("record 초기화","23131313")
        isRecording = false
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        readCallback = null
    }

    /**
     * 녹음된 데이터 반환 (ByteArrayOutputStream 형태)
     */
    fun getRawData(): ByteArrayOutputStream {
        return pcmStream
    }

    /**
     * 실시간 오디오 버퍼 처리 콜백 설정
     * @param callback (ShortArray, readSize) -> Unit
     */
    fun read(callback: (ShortArray, Int) -> Unit) {
        this.readCallback = callback
    }

}
