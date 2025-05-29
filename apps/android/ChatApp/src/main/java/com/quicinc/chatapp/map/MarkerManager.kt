package com.quicinc.chatapp.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.quicinc.chatapp.ui.LanguageManager
import com.quicinc.chatapp.utils.Constants
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor

class MarkerManager(
    private val context: Context,
    private val pointAnnotationManager: PointAnnotationManager,
    private val languageManager: LanguageManager
) {

    // 현재 마커 위치 추적 (중복 방지용)
    private var currentMarkerLocation: Point? = null

    // 토스트 중복 방지를 위한 변수들
    private var lastToastMessage = ""
    private var lastToastTime = 0L

    // 마커 추가 (개선된 버전)
    fun addMarker(point: Point): Point {
        try {
            Log.d("MarkerManager", "Adding marker at: ${point.longitude()}, ${point.latitude()}")

            // 현재 마커와 같은 위치인지 확인 (중복 방지)
            if (isSameLocation(currentMarkerLocation, point)) {
                Log.d("MarkerManager", "Marker already exists at this location, skipping")
                return point
            }

            // 기존 마커 삭제 (확실히 삭제)
            clearMarkers()

            // 리소스에서 비트맵 생성
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                android.R.drawable.ic_menu_mylocation
            )

            // 마커 옵션 설정
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)
                .withIconAnchor(IconAnchor.BOTTOM)
                .withIconSize(1.5)

            // 맵에 마커 추가
            val createdAnnotation = pointAnnotationManager.create(pointAnnotationOptions)
            Log.d("MarkerManager", "Marker created with ID: ${createdAnnotation.id}")

            // 현재 마커 위치 업데이트
            currentMarkerLocation = point

            // 마커 추가 메시지 표시 (토스트 중복 방지)
            val message = languageManager.getLocalizedString(
                "목적지 설정됨",
                "Destination set"
            )
            showDebouncedToast(message)

            Log.d("MarkerManager", "Marker successfully added")
            return point

        } catch (e: Exception) {
            Log.e("MarkerManager", "Error adding marker", e)
            val errorMessage = languageManager.getLocalizedString(
                "마커 설정 중 오류가 발생했습니다",
                "Error occurred while setting marker"
            )
            showDebouncedToast(errorMessage)
            return point
        }
    }

    // 모든 마커 삭제 (개선된 버전)
    fun clearMarkers() {
        try {
            val markerCount = pointAnnotationManager.annotations.size
            Log.d("MarkerManager", "Clearing $markerCount markers")

            pointAnnotationManager.deleteAll()
            currentMarkerLocation = null

            Log.d("MarkerManager", "All markers cleared")
        } catch (e: Exception) {
            Log.e("MarkerManager", "Error clearing markers", e)
        }
    }

    // 같은 위치인지 확인하는 함수
    private fun isSameLocation(location1: Point?, location2: Point?): Boolean {
        if (location1 == null || location2 == null) return false

        val distance = calculateDistance(
            location1.latitude(), location1.longitude(),
            location2.latitude(), location2.longitude()
        )

        // Constants 사용
        return distance < Constants.LOCATION_ACCURACY_THRESHOLD
    }

    // 거리 계산 함수
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // 지구 반지름 (km)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    // 토스트 메시지 중복 방지 함수
    private fun showDebouncedToast(message: String) {
        val currentTime = System.currentTimeMillis()

        // 같은 메시지이고 Constants에서 가져온 시간 이내라면 토스트 표시 안 함
        if (message == lastToastMessage && currentTime - lastToastTime < Constants.TOAST_DEBOUNCE_TIME) {
            return
        }

        lastToastMessage = message
        lastToastTime = currentTime
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 현재 마커 위치 반환 (디버깅용)
    fun getCurrentMarkerLocation(): Point? = currentMarkerLocation

    // 마커가 있는지 확인
    fun hasMarker(): Boolean = currentMarkerLocation != null

    // 마커 개수 반환 (디버깅용)
    fun getMarkerCount(): Int = pointAnnotationManager.annotations.size
}