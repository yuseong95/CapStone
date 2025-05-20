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
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var wakeWordDetector: PorcupineWakeWordDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CapstoneWhisperTheme {
                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }
                var permissionRequested by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                    permissionRequested = true
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission && !permissionRequested) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        permissionRequested = true
                    }
                }

                if (hasPermission) {
                    val context = this
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

    val silenceThreshold = 0.03f
    val silenceLimit = 2000L
    var lastSoundTime = System.currentTimeMillis()

    recorder.read { buffer, readSize ->
        val hasSound = buffer.take(readSize).any { abs(it / 32768.0f) > silenceThreshold }

        if (hasSound) {
            lastSoundTime = System.currentTimeMillis()
        }

        if (System.currentTimeMillis() - lastSoundTime >= silenceLimit) {
            recorder.stopRecording()
            isRecording.set(false)
            wakeWordDetector?.start()

            CoroutineScope(Dispatchers.IO).launch {
                val result = whisper.transcribe(recorder.getRawData())
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
        }
    }
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
