package com.quicinc.chatapp.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.map.MapInitializer
import com.quicinc.chatapp.map.MarkerManager
import com.quicinc.chatapp.search.SearchButtonViewModel
import com.quicinc.chatapp.ui.LanguageManager
import com.quicinc.chatapp.ui.NavigationUI
import com.quicinc.chatapp.utils.Constants
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeOfArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions

class NavigationManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val mapView: MapView,
    private val mapInitializer: MapInitializer,
    private val languageManager: LanguageManager,
    private val navigationUI: NavigationUI
) : RouteManager.OnRouteChangeListener, LocationManager.OnLocationChangeListener {

    // ViewModel 참조 추가
    private val searchButtonViewModel: SearchButtonViewModel?
        get() = (context as? Navi)?.searchButtonViewModel


    // 네비게이션 관련 변수들
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    // 턴 바이 턴 안내를 위한 변수들
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi

    // 위치 관리자와 마커 관리자
    private lateinit var locationManager: LocationManager
    private lateinit var routeManager: RouteManager
    private lateinit var markerManager: MarkerManager

    // 네비게이션 상태
    private var isNavigating = false

    // MapboxNavigation 인스턴스는 MainActivity에서 전달받음
    private lateinit var mapboxNavigation: MapboxNavigation

    // 카메라 초기화 추적
    private var hasInitializedCamera = false

    private var lastToastMessage = ""
    private var lastToastTime = 0L

    init {
        try {
            Log.d("NavigationManager", "Initializing navigation components")

            // 위치 관리자 초기화
            locationManager = LocationManager(mapView)

            // 네비게이션 컴포넌트 초기화
            initializeNavigationComponents()

            // 마커 관리자 초기화
            markerManager = MarkerManager(
                context,
                mapInitializer.pointAnnotationManager,
                languageManager
            )

            // 위치 변경 리스너 설정
            locationManager.setLocationChangeListener(this)

            // 맵 클릭 리스너 설정
            setupMapClickListener()

            Log.d("NavigationManager", "Navigation manager initialization phase 1 completed")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error initializing navigation components", e)
            throw IllegalStateException("Failed to initialize navigation components: ${e.message}")
        }
    }

    // MapboxNavigation 설정 메서드
    private fun setMapboxNavigation(navigation: MapboxNavigation) {
        Log.d("NavigationManager", "Setting MapboxNavigation instance")

        if (!::mapboxNavigation.isInitialized) {
            mapboxNavigation = navigation

            // 경로 관리자 초기화 - MapboxNavigation이 설정된 후에 초기화
            routeManager = RouteManager(context, mapboxNavigation, languageManager)
            routeManager.setRouteChangeListener(this)

            Log.d("NavigationManager", "Navigation manager initialization completed")
        }
    }

    private fun initializeNavigationComponents() {
        Log.d("NavigationManager", "Initializing navigation components")

        // 뷰포트 데이터 소스 초기화
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

        // 패딩 설정
        val pixelDensity = context.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )

        // 네비게이션 카메라 초기화
        navigationCamera = NavigationCamera(
            mapView.mapboxMap,
            mapView.camera,
            viewportDataSource
        )

        // 제스처 핸들러 추가
        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        // 카메라 상태 변경 관찰자 등록
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            val showButton = when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> false
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> true
            }
            navigationUI.setRecenterButtonVisibility(showButton)
        }

        // 경로 라인 API 및 뷰 초기화
        routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder()
                .vanishingRouteLineEnabled(true) // vanishing route line 기능 활성화
                .vanishingRouteLineUpdateIntervalNano(100_000_000L) // 업데이트 간격 설정 (0.1초)
                .build()
        )
        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context).build()
        )

        // 턴 바이 턴 안내 컴포넌트 초기화
        initializeTurnByTurnComponents()

        // 위치 컴포넌트 초기화
        mapView.location.apply {
            setLocationProvider(locationManager.getNavigationLocationProvider())
            locationPuck = createDefault2DPuck()
            enabled = true
        }

        Log.d("NavigationManager", "Navigation components initialized")
    }

    private fun initializeTurnByTurnComponents() {
        // 거리 포맷터 옵션
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(context).build()

        // 매뉴버 API 초기화
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )

        // 트립 프로그레스 API 초기화
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(context)
                .distanceRemainingFormatter(
                    DistanceRemainingFormatter(distanceFormatterOptions)
                )
                .timeRemainingFormatter(TimeRemainingFormatter(context))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeOfArrivalFormatter(EstimatedTimeOfArrivalFormatter(context))
                .build()
        )
    }

    private fun setupMapClickListener() {
        try {
            // gesture 플러그인을 사용하여 맵 클릭 리스너 설정
            mapView.gestures.addOnMapClickListener { point ->
                // 네비게이션 중이 아닐 때만 목적지 설정 가능
                if (!isNavigating && ::mapboxNavigation.isInitialized) {
                    Log.d("NavigationManager", "Map clicked - setting new destination")

                    // 마커 추가 및 목적지 설정
                    val destination = markerManager.addMarker(point)
                    routeManager.setDestination(destination)

                    // 목적지 설정 알림 (한 번만)
                    notifyDestinationSet()

                    return@addOnMapClickListener true
                }
                false
            }

            Log.d("NavigationManager", "Map click listener set up")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Failed to setup map click listener", e)
        }
    }

    // 관찰자 등록/해제 메서드 - MainActivity의 옵저버에서 호출됨
    @SuppressLint("MissingPermission")
    fun onNavigationAttached(mapboxNavigation: MapboxNavigation) {
        Log.d("NavigationManager", "Navigation attached")

        // MapboxNavigation 인스턴스 설정 - 코드 중복 제거를 위해 setMapboxNavigation() 호출
        setMapboxNavigation(mapboxNavigation)

        // 관찰자 등록 - setMapboxNavigation()에서 하지 않은 추가 작업들만 여기서 수행
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

        // 트립 세션 시작 - 위치 권한이 필요함
        mapboxNavigation.startTripSession()
    }

    fun onNavigationDetached(mapboxNavigation: MapboxNavigation) {
        Log.d("NavigationManager", "Navigation detached")

        // 관찰자 해제
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
    }

    // 경로 관찰자
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            Log.d("NavigationManager", "Routes updated: ${routeUpdateResult.navigationRoutes.size} routes")

            try {
                // 경로 라인 생성 및 렌더링
                routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                    mapView.mapboxMap.style?.apply {
                        routeLineView.renderRouteDrawData(this, value)
                    }
                }

                // 뷰포트 데이터 소스 경로 변경 알림
                viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
                viewportDataSource.evaluate()

                // 네비게이션 상태에 따라 카메라 위치 요청
                if (isNavigating) {
                    navigationCamera.requestNavigationCameraToFollowing()
                } else {
                    navigationCamera.requestNavigationCameraToOverview()
                }
            } catch (e: Exception) {
                Log.e("NavigationManager", "Error processing route update", e)
            }
        }
    }

    // 위치 관찰자
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {
            // Raw 위치는 사용하지 않음
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            // 현재 위치 포인트 생성
            val currentLocation = Point.fromLngLat(
                enhancedLocation.longitude,
                enhancedLocation.latitude
            )

            // 로그 추가
            Log.d("NavigationManager", "New location: ${enhancedLocation.longitude}, ${enhancedLocation.latitude}")

            // 위치 관리자에 위치 설정 (이 부분이 중요)
            locationManager.updateCurrentLocation(currentLocation)

            // 네비게이션 위치 제공자 업데이트
            locationManager.getNavigationLocationProvider().changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // 뷰포트 업데이트
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // vanishing route line 업데이트
            if (isNavigating) {
                updateVanishingRouteLine(currentLocation)
            }

            // 첫 위치 수신 시 카메라 위치 설정 - 이중 안전장치
            if (!hasInitializedCamera) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(currentLocation)
                        .zoom(15.0)
                        .build()
                )
                hasInitializedCamera = true
                Log.d("NavigationManager", "Camera initialized to user location in NavigationManager")
            }
        }
    }

    // vanishing route line 업데이트 메서드
    private fun updateVanishingRouteLine(currentLocation: Point) {
        try {
            // updateTraveledRouteLine은 콜백을 받지 않고 바로 결과를 반환
            val result = routeLineApi.updateTraveledRouteLine(currentLocation)

            // result 전체를 renderRouteLineUpdate에 전달
            mapView.mapboxMap.style?.let { style ->
                routeLineView.renderRouteLineUpdate(style, result)
            }

            // 로깅은 성공/실패에 따라 분기
            result.value?.let {
                Log.d("NavigationManager", "Vanishing route line updated for location: ${currentLocation.longitude()}, ${currentLocation.latitude()}")
            } ?: run {
                result.error?.let { error ->
                    Log.e("NavigationManager", "Error updating vanishing route line: ${error.errorMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error updating vanishing route line", e)
        }
    }

    // 경로 진행 관찰자
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        try {
            // 턴 안내 정보 업데이트
            val maneuvers = maneuverApi.getManeuvers(routeProgress)
            navigationUI.updateManeuverView(maneuvers)

            // 경로 진행 정보 업데이트
            val tripProgress = tripProgressApi.getTripProgress(routeProgress)
            navigationUI.updateTripProgressView(tripProgress)

            // vanishing route line 업데이트 (가장 간단한 방법)
            if (isNavigating) {
                routeLineApi.updateWithRouteProgress(routeProgress) { result ->
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderRouteLineUpdate(style, result)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error processing route progress", e)
        }
    }

    // 네비게이션 시작
    fun startNavigation() {
        if (::routeManager.isInitialized && routeManager.hasValidRoute()) {
            isNavigating = true

            // 내비게이션 모드별 카메라 설정
            applyCameraSettingsForNavigationMode()

            // UI 업데이트
            navigationUI.updateUIForNavigationStart()

            // ViewModel 상태 업데이트
            searchButtonViewModel?.setNavigationActive(true)

            // 경로 재요청
            routeManager.requestRoute()
        }
    }

    // 내비게이션 모드별 카메라 설정 적용
    private fun applyCameraSettingsForNavigationMode() {
        when (languageManager.currentNavigationMode) {
            Constants.NAVIGATION_MODE_WALKING -> {
                // 도보: 더 넓은 시야, 덜 기울어진 각도
                viewportDataSource.followingZoomPropertyOverride(16.0)
                viewportDataSource.followingPitchPropertyOverride(15.0)
                Log.d("NavigationManager", "Applied walking camera settings: zoom=16.0, pitch=15.0")
            }
            Constants.NAVIGATION_MODE_CYCLING -> {
                // 자전거: 중간 시야, 중간 각도
                viewportDataSource.followingZoomPropertyOverride(17.0)
                viewportDataSource.followingPitchPropertyOverride(25.0)
                Log.d("NavigationManager", "Applied cycling camera settings: zoom=17.0, pitch=25.0")
            }
            Constants.NAVIGATION_MODE_DRIVING -> {
                // 자동차: 가까운 시야, 3D 시점
                viewportDataSource.followingZoomPropertyOverride(20.0)
                viewportDataSource.followingPitchPropertyOverride(45.0)
                Log.d("NavigationManager", "Applied driving camera settings: zoom=20.0, pitch=45.0")
            }
        }
        viewportDataSource.evaluate()
    }

    fun cancelNavigation() {
        try {
            Log.d("NavigationManager", "Canceling navigation")

            isNavigating = false

            // 카메라 오버라이드 해제
            viewportDataSource.followingZoomPropertyOverride(null)
            viewportDataSource.followingPitchPropertyOverride(null)
            viewportDataSource.evaluate()

            // UI 업데이트
            navigationUI.updateUIForNavigationCancel()

            // ViewModel 상태 업데이트 - 목적지와 내비게이션 모두 리셋
            searchButtonViewModel?.setNavigationActive(false)
            searchButtonViewModel?.setHasDestination(false)

            // 경로 라인 명시적으로 지우기
            clearAllRouteLines()

            // 경로 및 마커 삭제
            if (::routeManager.isInitialized) {
                routeManager.clearRoutes()
            }

            if (::markerManager.isInitialized) {
                markerManager.clearMarkers()
            }

            Log.d("NavigationManager", "Navigation canceled successfully")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error canceling navigation", e)
        }
    }

    // 경로 라인 정리 메서드 추가
    private fun clearAllRouteLines() {
        if (::routeLineApi.isInitialized) {
            try {
                // 콜백 버전의 clearRouteLine 사용
                routeLineApi.clearRouteLine { clearRouteLineResult ->
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderClearRouteLineValue(style, clearRouteLineResult)
                    }

                    // 성공/실패 로깅
                    clearRouteLineResult.value?.let {
                        Log.d("NavigationManager", "Route line cleared successfully")
                    } ?: run {
                        clearRouteLineResult.error?.let { error ->
                            Log.e("NavigationManager", "Error clearing route line: ${error.errorMessage}")
                        }
                    }
                }

                // vanishing route line 초기화
                locationManager.getCurrentLocation()?.let { currentLocation ->
                    val resetResult = routeLineApi.updateTraveledRouteLine(currentLocation)
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderRouteLineUpdate(style, resetResult)
                    }
                }
            } catch (e: Exception) {
                Log.e("NavigationManager", "Error clearing route lines", e)
            }
        }
    }

    // 카메라 재중앙화
    fun recenterCamera() {
        Log.d("NavigationManager", "Recentering camera")
        navigationCamera.requestNavigationCameraToFollowing()
    }

    // 언어 변경 시 처리
    fun updateLanguage(newLanguage: String) {
        Log.d("NavigationManager", "Updating language to: $newLanguage")

        // 맵 스타일 다시 적용
        mapInitializer.applyMapStyle(newLanguage)

        // 경로가 있다면 경로 재요청
        if (::routeManager.isInitialized && routeManager.hasValidRoute()) {
            routeManager.requestRoute()
        }
    }

    // RouteManager.OnRouteChangeListener 구현
    override fun onRouteReady(routes: List<NavigationRoute>) {
        Log.d("NavigationManager", "Routes ready: ${routes.size}")

        // 경로 정보 계산 및 MainActivity에 전달
        if (routes.isNotEmpty()) {
            updateRouteInfoDisplay(routes.first())
        }
    }

    override fun onRouteError(message: String) {
        Log.e("NavigationManager", "Route error: $message")
        // 경로 오류 시 경로 정보 숨기기
        hideRouteInfoDisplay()
    }

    // 경로 정보 표시 업데이트
    private fun updateRouteInfoDisplay(route: NavigationRoute) {
        try {
            val routeOptions = route.directionsRoute
            val distance = routeOptions.distance()?.let { distanceInMeters ->
                if (distanceInMeters >= 1000) {
                    String.format("%.1f km", distanceInMeters / 1000)
                } else {
                    String.format("%.0f m", distanceInMeters)
                }
            } ?: "--"

            val duration = routeOptions.duration()?.let { durationInSeconds ->
                val hours = (durationInSeconds / 3600).toInt()
                val minutes = ((durationInSeconds % 3600) / 60).toInt()

                when {
                    hours > 0 -> String.format("%d시간 %d분", hours, minutes)
                    minutes > 0 -> String.format("%d분", minutes)
                    else -> "1분 미만"
                }
            } ?: "--"

            val arrivalTime = routeOptions.duration()?.let { durationInSeconds ->
                val currentTime = System.currentTimeMillis()
                val arrivalTimeMillis = currentTime + (durationInSeconds * 1000).toLong()
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = arrivalTimeMillis

                String.format("%02d:%02d",
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE)
                )
            } ?: "--"

            // MainActivity에 경로 정보 전달
            (context as? Navi)?.runOnUiThread {
                (context as Navi).updateRouteInfo(distance, duration, arrivalTime)
            }

            Log.d("NavigationManager", "Route info updated - Distance: $distance, Duration: $duration, Arrival: $arrivalTime")
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error updating route info display", e)
        }
    }

    // 경로 정보 숨기기
    private fun hideRouteInfoDisplay() {
        (context as? Navi)?.runOnUiThread {
            (context as Navi).hideRouteInfo()
        }
    }

    // LocationManager.OnLocationChangeListener 구현
    override fun onLocationChanged(location: Point) {
        try {
            Log.d("NavigationManager", "Location changed: ${location.longitude()}, ${location.latitude()}")

            // 현재 위치를 경로 관리자에 설정
            if (::routeManager.isInitialized) {
                routeManager.setOrigin(location)

                // 목적지가 처음 설정되었을 때만 알림 (중복 방지)
                if (routeManager.getDestination() != null) {
                    val viewModel = searchButtonViewModel
                    if (viewModel?.hasDestination?.value != true) {  // 상태가 false인 경우만
                        Log.d("NavigationManager", "First time both origin and destination available")
                        notifyDestinationSet()
                    }

                    // 네비게이션 중이고 목적지가 설정된 경우만 주기적으로 경로 업데이트
                    if (isNavigating && routeManager.getDestination() != null) {
                        routeManager.requestRoute()
                    }
                }
            } else {
                Log.w("NavigationManager", "RouteManager not initialized in onLocationChanged")
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error handling location change", e)
        }
    }

    // 목적지 설정 알림
    private fun notifyDestinationSet() {
        try {
            // ViewModel 상태 확인 후 업데이트
            val viewModel = searchButtonViewModel
            if (viewModel?.hasDestination?.value != true) {
                Log.d("NavigationManager", "Setting destination state to true")
                viewModel?.setHasDestination(true)

                // MainActivity에 직접 알림
                (context as? Navi)?.runOnUiThread {
                    (context as Navi).onDestinationSet()
                }

                Log.d("NavigationManager", "Destination set notification sent to MainActivity")
            } else {
                Log.d("NavigationManager", "Destination already set - skipping notification")
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error notifying destination set", e)
        }
    }

    // 네비게이션 상태 확인 메서드
    fun isNavigating(): Boolean {
        return isNavigating
    }

    private fun showDebouncedToast(message: String) {
        val currentTime = System.currentTimeMillis()

        // Constants에서 디바운싱 시간 가져오기
        val debounceTime = when {
            message.contains("오프라인") || message.contains("offline", true) ->
                Constants.OFFLINE_TOAST_DEBOUNCE_TIME
            message.contains("내비게이션") || message.contains("navigation", true) ->
                Constants.NAVIGATION_TOAST_DEBOUNCE_TIME
            else -> Constants.TOAST_DEBOUNCE_TIME
        }

        // 같은 메시지이고 지정된 시간 이내라면 토스트 표시 안 함
        if (message == lastToastMessage && currentTime - lastToastTime < debounceTime) {
            return
        }

        lastToastMessage = message
        lastToastTime = currentTime
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 목적지 설정 메서드
    fun setDestination(point: Point) {
        try {
            Log.d("NavigationManager", "Setting destination: ${point.longitude()}, ${point.latitude()}")

            if (::markerManager.isInitialized && ::routeManager.isInitialized) {
                // 기존 마커 및 경로 삭제 (중요: 순서 주의)
                Log.d("NavigationManager", "Clearing existing markers and routes")
                markerManager.clearMarkers()

                // 기존 경로도 명시적으로 클리어
                if (::mapboxNavigation.isInitialized) {
                    mapboxNavigation.setNavigationRoutes(emptyList())
                }

                // 경로 라인도 클리어
                clearAllRouteLines()

                // 새 마커 추가
                Log.d("NavigationManager", "Adding new marker")
                markerManager.addMarker(point)

                // 경로 관리자에 목적지 설정 (이때 자동으로 새 경로 요청됨)
                Log.d("NavigationManager", "Setting new destination in RouteManager")
                routeManager.setDestination(point)

                // 즉시 목적지 설정 알림
                notifyDestinationSet()

                // 오프라인 상태에서 목적지 설정 시 안내 메시지 (토스트 개선)
                val isOffline = !isNetworkAvailable()
                if (isOffline) {
                    val message = languageManager.getLocalizedString(
                        "오프라인 모드: 다운로드된 지역 내에서 내비게이션이 가능합니다",
                        "Offline mode: Navigation available within downloaded areas"
                    )
                    showDebouncedToast(message)
                }

                Log.d("NavigationManager", "Destination set successfully (${if(isOffline) "offline" else "online"} mode)")
            } else {
                Log.e("NavigationManager", "MarkerManager or RouteManager not initialized")
                throw IllegalStateException("Navigation components not properly initialized")
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error setting destination", e)
            val message = languageManager.getLocalizedString(
                "목적지 설정 중 오류가 발생했습니다: ${e.message}",
                "Error setting destination: ${e.message}"
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // 오류 발생 시 UI 상태도 초기화
            searchButtonViewModel?.setHasDestination(false)
        }
    }


    fun getRouteManager(): RouteManager {
        if (!::routeManager.isInitialized) {
            throw IllegalStateException("RouteManager not initialized")
        }
        return routeManager
    }

    fun getMarkerManager(): MarkerManager {
        if (!::markerManager.isInitialized) {
            throw IllegalStateException("MarkerManager not initialized")
        }
        return markerManager
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            return networkInfo.isConnected
        }
    }

    fun checkNetworkStatus() {
        try {
            val isOnline = isNetworkAvailable()
            /*val message = if (isOnline) {
                languageManager.getLocalizedString(
                    "온라인 모드: 검색 및 내비게이션 기능 모두 사용 가능",
                    "Online mode: Search and navigation features available"
                )
            } else {
                languageManager.getLocalizedString(
                    "오프라인 모드: 내비게이션 사용 가능 (검색 기능은 온라인 필요)",
                    "Offline mode: Navigation available (Search requires online connection)"
                )
            }

            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()*/

            // MainActivity에 네트워크 상태 알림
            if (context is Navi) {
                context.setSearchButtonEnabled(isOnline)
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error checking network status", e)
        }
    }

    // 주기적으로 네트워크 상태 확인하는 메소드
    fun startNetworkMonitoring() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 네트워크 연결됨
                (context as? Navi)?.runOnUiThread {
                    // 검색 버튼 활성화
                    (context as? Navi)?.setSearchButtonEnabled(true)

                    /*val message = languageManager.getLocalizedString(
                        "온라인 모드로 전환: 검색 기능 사용 가능",
                        "Switched to online mode: Search feature available"
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()*/
                }
            }

            override fun onLost(network: Network) {
                // 네트워크 연결 끊김
                (context as? Navi)?.runOnUiThread {
                    // 검색 버튼 비활성화
                    (context as? Navi)?.setSearchButtonEnabled(false)

                    /*val message = languageManager.getLocalizedString(
                        "오프라인 모드로 전환: 내비게이션만 사용 가능",
                        "Switched to offline mode: Navigation only"
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()*/
                }
            }
        })
    }

    fun clearDestination() {
        try {
            Log.d("NavigationManager", "Clearing destination only (not navigation)")

            // 1. 경로 라인 클리어
            clearAllRouteLines()

            // 2. 마커 삭제
            if (::markerManager.isInitialized) {
                markerManager.clearMarkers()
            }

            // 3. 경로 관리자에서 목적지 제거
            if (::routeManager.isInitialized) {
                routeManager.clearDestination()
            }

            // 4. Mapbox Navigation의 경로도 클리어
            if (::mapboxNavigation.isInitialized) {
                mapboxNavigation.setNavigationRoutes(emptyList())
            }

            // 5. ViewModel 상태 업데이트
            searchButtonViewModel?.setHasDestination(false)

            // 6. MainActivity에 경로 정보 숨기기 알림
            hideRouteInfoDisplay()

            Log.d("NavigationManager", "Destination cleared successfully")

        } catch (e: Exception) {
            Log.e("NavigationManager", "Error clearing destination", e)
        }
    }

    // 정리 메소드
    fun cleanup() {
        try {
            Log.d("NavigationManager", "Cleaning up navigation resources")
            // NavigationManager에서는 아무것도 정리하지 않음
            // MainActivity에서 MapboxNavigationApp.disable() 호출
        } catch (e: Exception) {
            Log.e("NavigationManager", "Error during cleanup", e)
        }
    }
}