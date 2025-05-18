package com.example.capstone_whisper

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.capstone_whisper.asr.Whisper
import com.example.capstone_whisper.engine.WhisperEngine
import com.example.capstone_whisper.ui.theme.CapstoneWhisperTheme
import com.example.cpastone.Recorder
import java.io.File

class MainActivity : ComponentActivity() {
    private val ENGLISH_ONLY_MODEL_EXTENSION: String = ".en.tflite"

    private val DEFAULT_MODEL_TO_USE: String = "whisper-tiny.tflite"
    private val ENGLISH_ONLY_VOCAB_FILE: String = "filters_vocab_en.bin"
    private val MULTILINGUAL_VOCAB_FILE: String = "filters_vocab_multilingual.bin"
    private val EXTENSIONS_TO_COPY: Array<String> = arrayOf("tflite", "bin", "wav", "pcm")

    private var sdcardDataFolder: File? = null
    private var selectedWaveFile: File? = null
    private var selectedTfliteFile: File? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CapstoneWhisperTheme {
                // 초기 권한 상태 확인
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

                // 처음 실행 시에만 권한 요청
                LaunchedEffect(Unit) {
                    if (!hasPermission && !permissionRequested) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        permissionRequested = true
                    }
                }

                when {
                    hasPermission -> {
                        val audioRecorder = remember { Recorder(this) }
                        val whisper = remember { Whisper(this) }

                        AudioRecorderScreen(audioRecorder, whisper)
                    }


                    permissionRequested -> {
                        NoPermissionScreen()
                    }

                    else -> {
                        // 초기 요청 전 로딩 화면
                        LoadingScreen()
                    }
                }
            }
        }
    }
}
@Composable
fun NoPermissionScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("권한이 필요합니다. 설정에서 앱 권한을 허용해주세요.")
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AudioRecorderScreen(
    audioRecorder: Recorder,
    whisper: Whisper
) {
    var isRecording by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("여기에 변환된 텍스트가 표시됩니다.") }
    var isUploading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("음성 녹음 및 변환") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    if (!isRecording) audioRecorder.startRecording() else audioRecorder.stopRecording()
                    isRecording = !isRecording
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRecording) "녹음 중지" else "녹음 시작")
            }

            Button(
                onClick = {
                    isUploading = true
                    audioRecorder.stopRecording()
                    val result = whisper.transcribe(audioRecorder.getRawData())
                    transcribedText = result
                    isRecording = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Text("음성 변환")
            }

            if (isUploading) {
                Box(Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            Text(
                text = transcribedText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}