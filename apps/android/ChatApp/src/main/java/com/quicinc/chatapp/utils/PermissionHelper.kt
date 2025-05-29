package com.quicinc.chatapp.utils;

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.ui.LanguageManager

class PermissionHelper(
    private val activity: Navi,
    private val languageManager: LanguageManager
) {
    private var permissionCallback: PermissionCallback? = null

    // 권한 요청 결과 처리
    private val locationPermissionRequest = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    // 권한 허용됨
                    permissionCallback?.onPermissionGranted()
                }
                else -> {
                    // 권한 거부됨
                    val message = languageManager.getLocalizedString(
                        "위치 권한이 거부되었습니다. 설정에서 권한을 활성화해주세요.",
                        "Location permissions denied. Please enable permissions in settings."
                    )
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    permissionCallback?.onPermissionDenied()
                }
            }
        } catch (e: Exception) {
            Log.e("PermissionHelper", "Error handling permission result", e)
            Toast.makeText(
                activity,
                "권한 처리 중 오류가 발생했습니다: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun setPermissionCallback(callback: PermissionCallback) {
        permissionCallback = callback
    }

    fun checkLocationPermissions() {
        if (hasLocationPermissions()) {
            // 이미 권한 있음
            permissionCallback?.onPermissionGranted()
        } else {
            // 권한 요청
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        // 원본 코드와 동일한 방식으로 권한 요청
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
}