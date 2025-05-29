package com.quicinc.chatapp.navigation
import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

class LocationManager(
    private val mapView: MapView
) {
    private val navigationLocationProvider = NavigationLocationProvider()

    // 현재 위치 (경도, 위도)
    private var currentLocation: Point? = null

    // 카메라가 초기화되었는지 추적하는 플래그
    private var hasInitializedCamera = false

    // 위치 변경 리스너 인터페이스
    interface OnLocationChangeListener {
        fun onLocationChanged(location: Point)
    }

    // 위치 변경 리스너
    private var locationChangeListener: OnLocationChangeListener? = null

    init {
        Log.d("LocationManager", "Initializing location component")
        initializeLocationComponent()
    }

    private fun initializeLocationComponent() {
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }
    }

    fun setLocationChangeListener(listener: OnLocationChangeListener) {
        Log.d("LocationManager", "Setting location change listener")
        locationChangeListener = listener
    }

    fun getCurrentLocation(): Point? = currentLocation

    fun getNavigationLocationProvider(): NavigationLocationProvider = navigationLocationProvider

    // LocationObserver 내부 함수 대신 이 함수를 직접 호출하여 현재 위치 업데이트
    fun updateCurrentLocation(location: Point) {
        Log.d("LocationManager", "Updating current location: ${location.longitude()}, ${location.latitude()}")

        currentLocation = location

        // 위치 변경 리스너에게 알림
        locationChangeListener?.onLocationChanged(location)

        // 처음 위치를 받았을 때 카메라 위치 설정
        if (!hasInitializedCamera) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(location)
                    .zoom(15.0)
                    .build()
            )
            hasInitializedCamera = true
            Log.d("LocationManager", "Camera initialized to user location")
        }
    }
}