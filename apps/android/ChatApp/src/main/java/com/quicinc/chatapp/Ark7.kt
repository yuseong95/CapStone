package com.quicinc.chatapp/*package com.quicinc.chatapp;

import android.Manifest
import android.R.attr.visibility
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMapsOptions
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeOfArrivalFormatter
import kotlinx.coroutines.launch
import java.io.File

class Ark7 : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val navigationLocationProvider = NavigationLocationProvider()
    private lateinit var tileStore: TileStore
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var prefs: SharedPreferences
    private lateinit var recenterButton: Button

    // 턴 바이 턴 안내를 위한 변수들
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var maneuverView: MapboxManeuverView
    private lateinit var tripProgressView: MapboxTripProgressView

    // 언어 설정을 위한 상수
    companion object {
        const val PREFS_NAME = "NaviSettings"
        const val PREF_LANG_KEY = "language"
        const val LANG_KOREAN = "ko"
        const val LANG_ENGLISH = "en"
    }

    // 현재 선택된 언어
    private var selectedLanguage = LANG_KOREAN // 디폴트 한국어

    // Variables to store the origin and destination points
    private var currentOrigin: Point? = null
    private var currentDestination: Point? = null

    // 경로 요청 디바운싱을 위한 변수
    private var lastRouteRequestTime = 0L
    private val ROUTE_REQUEST_DEBOUNCE_TIME = 3000 // 3초

    // Flag to track if we've already centered the camera on user's location
    private var hasInitializedCamera = false

    // UI components
    private lateinit var startNavigationButton: Button
    private lateinit var cancelButton: Button
    private lateinit var buttonLayout: LinearLayout
    private lateinit var languageRadioGroup: RadioGroup

    // Track if navigation is active
    private var isNavigating = false

    // Activity result launcher for location permissions
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                else -> {
                    val message = if (selectedLanguage == LANG_KOREAN) {
                        "위치 권한이 거부되었습니다. 설정에서 권한을 활성화해주세요."
                    } else {
                        "Location permissions denied. Please enable permissions in settings."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize shared preferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved language preference or use Korean as default
        selectedLanguage = prefs.getString(PREF_LANG_KEY, LANG_KOREAN) ?: LANG_KOREAN

        // XML 레이아웃 설정 (rootLayout 대신 바로 사용)
        setContentView(R.layout.activity_main)

        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

        // Initialize TileStore
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        tileStore = TileStore.create(tilePath)

        // Check if tile regions already exist before downloading
        checkTileRegionExists()

        // 이제 MapView를 따로 생성하지 않고 XML에서 참조
        mapView = findViewById(R.id.mapView)

        // UI 컴포넌트 초기화 및 설정
        setupUIComponents()

        // 권한 확인 후 맵 초기화가 진행되도록 수정
        checkLocationPermissions()
    }

    // 새로운 메서드: 권한 확인 로직을 분리
    private fun checkLocationPermissions() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are already granted
            initializeMapComponents()
        } else {
            // Request location permissions
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun setupUIComponents() {
        // XML에서 UI 컴포넌트 참조
        buttonLayout = findViewById(R.id.buttonLayout)
        startNavigationButton = findViewById(R.id.startNavigationButton)
        cancelButton = findViewById(R.id.cancelButton)
        recenterButton = findViewById(R.id.recenterButton)
        languageRadioGroup = findViewById(R.id.languageRadioGroup)
        maneuverView = findViewById(R.id.maneuverView)
        tripProgressView = findViewById(R.id.tripProgressView)

        // Radio 버튼 참조
        val radioButtonKo = findViewById<RadioButton>(R.id.radioButtonKo)
        val radioButtonEn = findViewById<RadioButton>(R.id.radioButtonEn)

        // 초기 언어 설정에 따라 라디오 버튼 상태 설정
        radioButtonKo.isChecked = selectedLanguage == LANG_KOREAN
        radioButtonEn.isChecked = selectedLanguage == LANG_ENGLISH

        // 버튼 클릭 리스너 설정
        startNavigationButton.setOnClickListener {
            startNavigation()
        }

        cancelButton.setOnClickListener {
            cancelNavigation()
        }

        recenterButton.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
        }

        // 언어 변경 리스너 설정
        languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLanguage = if (checkedId == R.id.radioButtonKo) LANG_KOREAN else LANG_ENGLISH
            changeLanguage(newLanguage)
        }

        // 초기 UI 상태 설정
        startNavigationButton.isEnabled = false
        cancelButton.visibility = View.GONE
        recenterButton.visibility = View.GONE
        maneuverView.visibility = View.GONE
        tripProgressView.visibility = View.GONE
    }

    // 언어 변경 메서드
    private fun changeLanguage(newLanguage: String) {
        if (selectedLanguage == newLanguage) return

        selectedLanguage = newLanguage

        // 언어 설정 저장
        prefs.edit().putString(PREF_LANG_KEY, selectedLanguage).apply()

        // UI 텍스트 업데이트
        startNavigationButton.text = if (selectedLanguage == LANG_KOREAN) "내비게이션 시작" else "Start Navigation"
        cancelButton.text = if (selectedLanguage == LANG_KOREAN) "내비게이션 취소" else "Cancel Navigation"
        recenterButton.text = if (selectedLanguage == LANG_KOREAN) "위치로 돌아가기" else "Return to Route"

        // 지도 스타일 다시 로드 (한국어/영어 라벨 적용)
        applyMapStyle()

        // 만약 경로가 있다면 경로 다시 요청 (새 언어로 안내 생성)
        if (currentOrigin != null && currentDestination != null) {
            requestRoute()
        }

        // 언어 변경 알림
        val message = if (selectedLanguage == LANG_KOREAN) "언어가 한국어로 변경되었습니다." else "Language changed to English."
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun applyMapStyle() {
        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            // 한국어 라벨 우선 적용 (글로벌 설정)
            if (selectedLanguage == LANG_KOREAN) {
                // 주요 라벨 레이어의 이름과 필드를 한국어로 설정
                val labelLayers = listOf(
                    "country-label",
                    "road-label",
                    "settlement-label",
                    "poi-label",
                    "water-point-label",
                    "water-line-label"
                )

                // 각 레이어의 text-field 속성을 한국어 필드로 변경
                labelLayers.forEach { layerId ->
                    // Use getLayerAs to get the layer as a SymbolLayer and then set properties
                    style.getLayerAs<com.mapbox.maps.extension.style.layers.generated.SymbolLayer>(layerId)?.let { layer ->
                        // Set text field to Korean field
                        layer.textField("{name_ko}")
                    }
                }
            }
        }
    }


    private fun checkTileRegionExists() {
        val regionName = "seoul-incheon-gyeonggi"

        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!
                val regionExists = regions.any { it.id == regionName }

                if (!regionExists) {
                    // Download tiles only if the region doesn't exist
                    downloadOfflineTiles()
                } else {
                    Log.d("OfflineTiles", "Region $regionName already exists")
                }
            } else {
                // Error checking regions, try downloading anyway
                downloadOfflineTiles()
            }
        }
    }

    private fun initializeMapComponents() {
        MapboxMapsOptions.tileStore = tileStore

        // 지도 스타일 적용 (한국어/영어 라벨)
        applyMapStyle()

        // Initialize location puck
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D()
            enabled = true
        }

        // Set viewportDataSource, which tells the navigationCamera where to look
        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

        // Set padding for the navigation camera
        val pixelDensity = this.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )

        // Initialize a NavigationCamera
        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)

        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        // Initialize route line API and view for drawing the route on the map
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(MapboxRouteLineViewOptions.Builder(this).build())

        // Initialize annotation manager for destination marker
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

        // Set up map click listener to set destination
        mapView.mapboxMap.addOnMapClickListener { point ->
            // Only allow setting destination if not actively navigating
            if (!isNavigating) {
                setDestination(point)
            }
            true
        }

        // 카메라 상태 관찰 로직 추가
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> recenterButton.visibility = View.GONE
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> recenterButton.visibility = View.VISIBLE
            }
        }

        // 턴 바이 턴 안내를 위한 API 초기화
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(DistanceFormatterOptions.Builder(this).build())
        )

        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(this)
                .distanceRemainingFormatter(DistanceRemainingFormatter(
                    DistanceFormatterOptions.Builder(this).build()
                ))
                .timeRemainingFormatter(TimeRemainingFormatter(this))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeOfArrivalFormatter(EstimatedTimeOfArrivalFormatter(this))
                .build()
        )
    }

    // Set destination point and add a marker
    private fun setDestination(point: Point) {
        // Update current destination
        currentDestination = point

        // Clear existing annotations
        pointAnnotationManager.deleteAll()

        // Create a bitmap from a drawable resource
        val bitmap = BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_mylocation)

        // Create a destination marker
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage(bitmap)
            .withIconAnchor(IconAnchor.BOTTOM)
            .withIconSize(1.5)

        // Add the annotation to the map
        pointAnnotationManager.create(pointAnnotationOptions)

        // Enable start navigation button if origin is also available
        if (currentOrigin != null) {
            startNavigationButton.isEnabled = true
            // Preview the route immediately
            requestRoute()
        }

        val message = if (selectedLanguage == LANG_KOREAN) "목적지 설정됨" else "Destination set"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Start navigation mode
    private fun startNavigation() {
        if (currentOrigin != null && currentDestination != null) {
            isNavigating = true

            // 운전 시점으로 카메라 줌/피치 오버라이드
            viewportDataSource.followingZoomPropertyOverride(20.0) // 운전 시 가까운 줌
            viewportDataSource.followingPitchPropertyOverride(45.0) // 3D 시점
            viewportDataSource.evaluate()

            // Update UI
            startNavigationButton.visibility = View.GONE
            cancelButton.visibility = View.VISIBLE

            // 턴 바이 턴 안내 UI 표시
            maneuverView.visibility = View.VISIBLE
            tripProgressView.visibility = View.VISIBLE

            // Request routes with latest origin and destination
            requestRoute()
        }
    }

    // Cancel navigation mode
    private fun cancelNavigation() {
        isNavigating = false

        // 카메라 오버라이드 해제
        viewportDataSource.followingZoomPropertyOverride(null)
        viewportDataSource.followingPitchPropertyOverride(null)
        viewportDataSource.evaluate()

        // Update UI
        cancelButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
        recenterButton.visibility = View.GONE // 네비게이션 취소 시 숨김

        // 턴 바이 턴 안내 UI 숨김
        maneuverView.visibility = View.GONE
        tripProgressView.visibility = View.GONE

        // Clear routes
        mapboxNavigation.setNavigationRoutes(emptyList())

        // 경로 라인 명시적으로 지우기
        lifecycleScope.launch {
            routeLineApi.clearRouteLine { value ->
                mapView.mapboxMap.getStyle()?.let { style ->
                    routeLineView.renderClearRouteLineValue(style, value)
                }
            }
        }

        // Clear the destination marker
        pointAnnotationManager.deleteAll()
        currentDestination = null

        // Disable start button until new destination is selected
        startNavigationButton.isEnabled = false
    }

    // Routes observer draws a route line and origin/destination circles on the map
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            // Generate route geometries asynchronously and render them
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply { routeLineView.renderRouteDrawData(this, value) }
            }

            // Update viewportSourceData to include the new route
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()

            // Set the navigationCamera to OVERVIEW or FOLLOWING based on state
            if (isNavigating) {
                navigationCamera.requestNavigationCameraToFollowing()
            } else {
                navigationCamera.requestNavigationCameraToOverview()
            }
        }
    }

    // Location observer updates the location puck and camera to follow the user's location
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            // Update our current origin point with the latest location
            currentOrigin = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)

            // Update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // Only move the camera to the user's location the first time we get a location
            if (!hasInitializedCamera) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
                        .zoom(15.0)
                        .build()
                )

                // Mark camera as initialized
                hasInitializedCamera = true

                Log.d("Navigation", "Camera initialized to user location: ${enhancedLocation.longitude}, ${enhancedLocation.latitude}")
            }

            // Update viewportDataSource to trigger camera to follow the location during navigation
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // If actively navigating, update the route with current position - 하지만 너무 자주 요청하지 않도록 제한
            if (isNavigating && currentDestination != null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRouteRequestTime > ROUTE_REQUEST_DEBOUNCE_TIME) {
                    requestRoute()
                    lastRouteRequestTime = currentTime
                }
            }

            // Enable start button if we have both origin and destination
            if (currentDestination != null && !isNavigating) {
                startNavigationButton.isEnabled = true
            }
        }
    }

    // 경로 진행 상황 관찰자
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // 턴 안내 UI 업데이트
        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuverView.renderManeuvers(maneuvers)

        // 경로 진행 정보 UI 업데이트
        val tripProgress = tripProgressApi.getTripProgress(routeProgress)
        tripProgressView.render(tripProgress)
    }

    // Define MapboxNavigation
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                // Register observers
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)

                // Start trip session to get real location updates
                mapboxNavigation.startTripSession()

                // 경로 진행 관찰자 등록
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                // Clean up observers
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)

                // 경로 진행 관찰자 해제
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
            }
        },
        onInitialize = this::initNavigation
    )

    private fun downloadOfflineTiles() {
        // 서울/인천/경기도 지역을 대략적으로 커버하는 사각형 영역 생성
        val seoulAreaBounds = listOf(
            Point.fromLngLat(126.5, 37.3),  // 서쪽 하단 (범위 확장)
            Point.fromLngLat(127.5, 37.3),  // 동쪽 하단 (범위 확장)
            Point.fromLngLat(127.5, 38.0),  // 동쪽 상단 (범위 확장)
            Point.fromLngLat(126.5, 38.0),  // 서쪽 상단 (범위 확장)
            Point.fromLngLat(126.5, 37.3)   // 시작점으로 닫기 (범위 확장)
        )

        // 위 좌표로 Polygon 생성
        val regionGeometry = Polygon.fromLngLats(listOf(seoulAreaBounds))

        // OfflineManager 생성
        val offlineManager = OfflineManager()

        // tilesetDescriptor 생성
        val tilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.MAPBOX_STREETS) // 기본 스트리트 스타일로 변경
                .minZoom(6)  // 넓은 영역을 위한 최소 줌
                .maxZoom(16) // 상세 내비게이션을 위한 최대 줌
                .build()
        )

        // 내비게이션을 위한 추가 타일셋
        val navigationTilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/navigation-day-v1")
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // 3. TileRegionLoadOptions 생성 - 두 타일셋 모두 포함
        val tileRegionLoadOptions = TileRegionLoadOptions.Builder()
            .geometry(regionGeometry)
            .acceptExpired(true)
            .descriptors(listOf(tilesetDescriptor, navigationTilesetDescriptor))
            .build()

        // 타일 리전 이름
        val regionName = "seoul-incheon-gyeonggi"

        // 진행 상황 모니터링 콜백
        val progressCallback: (TileRegionLoadProgress) -> Unit = { progress ->
            val percentage = if (progress.requiredResourceCount > 0) {
                (progress.completedResourceCount.toFloat() / progress.requiredResourceCount) * 100
            } else {
                0f
            }

            // 다운로드 진행률을 로그로 출력 (실제 앱에서는 UI에 표시할 수 있음)
            Log.d("OfflineTiles", "Download progress: ${percentage.toInt()}%")
        }

        // 타일 리전 다운로드
        tileStore.loadTileRegion(
            regionName,
            tileRegionLoadOptions,
            progressCallback
        ) { expected ->
            if (expected.isValue) {
                // 다운로드 성공
                val tileRegion = expected.value!!
                Log.d("OfflineTiles", "Download completed successfully! Region: ${tileRegion.id}")

                // 성공 메시지 표시
                runOnUiThread {
                    val message = if (selectedLanguage == LANG_KOREAN)
                        "오프라인 지도 다운로드 완료"
                    else
                        "Offline map download completed"

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } else {
                // 다운로드 실패
                val error = expected.error!!
                Log.e("OfflineTiles", "Download failed: ${error.message}")

                runOnUiThread {
                    val message = if (selectedLanguage == LANG_KOREAN)
                        "오프라인 지도 다운로드 실패: ${error.message}"
                    else
                        "Offline map download failed: ${error.message}"

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Request route between current origin and destination
    private fun requestRoute() {
        val origin = currentOrigin ?: return
        val destination = currentDestination ?: return

        // 로그로 경로 요청 정보 기록
        Log.d("Navigation", "Requesting route from: ${origin.longitude()},${origin.latitude()} to: ${destination.longitude()},${destination.latitude()}")

        // 이 시점에서 lastRouteRequestTime 업데이트
        lastRouteRequestTime = System.currentTimeMillis()

        // 경로 요청 옵션 빌드
        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(origin, destination))
            .layersList(listOf(mapboxNavigation.getZLevel(), null))
            .language(selectedLanguage) // 선택된 언어로 경로 안내 설정
            .steps(true) // 턴 바이 턴 안내를 위해 필요
            .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("Navigation", "Route request canceled")
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("Navigation", "Route request failed: ${reasons.firstOrNull()?.message}")
                    runOnUiThread {
                        val message = if (selectedLanguage == LANG_KOREAN)
                            "경로를 찾을 수 없습니다: ${reasons.firstOrNull()?.message}"
                        else
                            "Could not find route: ${reasons.firstOrNull()?.message}"

                        Toast.makeText(this@Ark7, message, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    Log.d("Navigation", "Routes ready: ${routes.size} routes, origin: $routerOrigin")
                    mapboxNavigation.setNavigationRoutes(routes)
                }
            }
        )
    }

    // On initialization of MapboxNavigation
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        // 1. 먼저 tileStore 기반 RoutingTilesOptions 만들기
        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        // 2. 위 옵션을 포함한 NavigationOptions 만들기 - timeouts 증가 (밀리초 단위)
        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000) // 기본값 보다 큰 값으로 설정
            .build()

        // 3. MapboxNavigationApp 초기화
        MapboxNavigationApp.setup(navOptions)

        // Initialize location puck
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up MapboxNavigation to prevent memory leaks
        MapboxNavigationApp.disable()
    }
}*/