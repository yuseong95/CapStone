@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.capstone_whisper

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.capstone_whisper.asr.PorcupineWakeWordDetector
import com.example.capstone_whisper.asr.Recorder
import com.example.capstone_whisper.asr.Whisper
import com.example.capstone_whisper.ui.theme.CapstoneWhisperTheme
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.example.capstone_whisper.utils.rememberAudioPermissionState
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var wakeWordDetector: PorcupineWakeWordDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CapstoneWhisperTheme {
                val context = this
                var permissionRequested by remember { mutableStateOf(false) }

                val hasPermission by rememberAudioPermissionState(context) {
                    permissionRequested = true
                }

                if (hasPermission) {
                    val audioRecorder = remember { Recorder(context) }
                    val whisper = remember { Whisper(context) }
                    val isRecording = remember { AtomicBoolean(false) }

                    var transcribedText by remember { mutableStateOf("여기에 변환된 텍스트가 표시됩니다.") }
                    var isUploading by remember { mutableStateOf(false) }
                    var wakeWordDetected by remember { mutableStateOf(false) }

                    wakeWordDetector = PorcupineWakeWordDetector(
                        context = context,
                        onWakeWordDetected = {
                            runOnUiThread {
                                if (!isUploading && isRecording.compareAndSet(false, true)) {
                                    Toast.makeText(context, "whisper detected!", Toast.LENGTH_SHORT).show()
                                    wakeWordDetected = true
                                    playDetectVoiceAndStart(context, audioRecorder, whisper, wakeWordDetector, isRecording,
                                        onStart = {
                                            isUploading = true
                                            wakeWordDetected = false
                                        },
                                        onComplete = {
                                            transcribedText = it
                                            isUploading = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                    wakeWordDetector?.start()

                    AudioStatusScreen(isUploading, transcribedText, wakeWordDetected)
                } else {
                    if (!permissionRequested) LoadingScreen() else NoPermissionScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeWordDetector?.stop()
    }
}

fun startRecordingAndTranscribe(
    recorder: Recorder,
    whisper: Whisper,
    wakeWordDetector: PorcupineWakeWordDetector?,
    isRecording: AtomicBoolean,
    onStart: () -> Unit,
    onComplete: (String) -> Unit
) {
    onStart()
    wakeWordDetector?.stop()
    recorder.startRecording()

    isRecording.set(true)

    val silenceThreshold = 0.01f
    val silenceLimit = 1000L
    var lastSoundTime = System.currentTimeMillis()

    var alreadyStopped = false

    recorder.read { buffer, readSize ->
        if (alreadyStopped) return@read

        val hasSound = !isSilent(buffer, readSize, silenceThreshold)

        if (hasSound) {
            lastSoundTime = System.currentTimeMillis()
        }

        if (System.currentTimeMillis() - lastSoundTime >= silenceLimit) {
            alreadyStopped = true  // ✅ 재호출 방지
            recorder.stopRecording()
            isRecording.set(false)
            wakeWordDetector?.start()

            CoroutineScope(Dispatchers.IO).launch {
                val raw = recorder.getRawData()
                if (raw.size() == 0) {
                    withContext(Dispatchers.Main) {
                        onComplete("⚠️ 음성이 감지되지 않았습니다.")
                    }
                    return@launch
                }
                val result = whisper.transcribe(raw)
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
        }
    }

}

fun isSilent(buffer: ShortArray, readSize: Int, threshold: Float): Boolean {
    var sum = 0.0
    for (i in 0 until readSize) {
        val sample = buffer[i] / 32768.0f
        sum += sample * sample
    }
    val rms = kotlin.math.sqrt(sum / readSize)
    return rms < threshold
}

fun playDetectVoiceAndStart(
    context: Context,
    recorder: Recorder,
    whisper: Whisper,
    wakeWordDetector: PorcupineWakeWordDetector?,
    isRecording: AtomicBoolean,
    onStart: () -> Unit,
    onComplete: (String) -> Unit
) {
    try {
        val afd = context.assets.openFd("detect_voice.wav")
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            prepare()
            start()
        }

        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
            startRecordingAndTranscribe(recorder, whisper, wakeWordDetector, isRecording, onStart, onComplete)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        startRecordingAndTranscribe(recorder, whisper, wakeWordDetector, isRecording, onStart, onComplete)
    }
}

@Composable
fun NoPermissionScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("권한이 필요합니다. 설정에서 앱 권한을 허용해주세요.")
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun AudioStatusScreen(isUploading: Boolean, transcribedText: String, wakeWordDetected: Boolean) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("음성 인식 대기 중") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wakeWordDetected) {
                Text(
                    text = "🔊 'whisper' 감지됨! 음성 인식 시작...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isUploading) {
                Box(Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Text(
                    text = "녹음중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = transcribedText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
