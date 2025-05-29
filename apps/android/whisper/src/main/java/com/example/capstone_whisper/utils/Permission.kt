package com.example.capstone_whisper.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

@Composable
fun rememberAudioPermissionState(
    context: Context,
    onPermissionResult: (Boolean) -> Unit
): State<Boolean> {
    val permissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted.value = isGranted
        onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted.value) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return permissionGranted
}
