package com.quicinc.chatapp.navigation
import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.quicinc.chatapp.ui.LanguageManager
import com.quicinc.chatapp.utils.Constants
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import android.net.ConnectivityManager

class RouteManager(
    private val context: Context,
    private val mapboxNavigation: MapboxNavigation,
    private val languageManager: LanguageManager
) {
    // 현재 출발지와 목적지
    private var currentOrigin: Point? = null
    private var currentDestination: Point? = null

    // 경로 요청 디바운싱을 위한 변수
    private var lastRouteRequestTime = 0L

    // 경로 정보 캐싱 지원을 위한 필드
    private var cachedRoutes: List<NavigationRoute>? = null

    // 사용자 액션으로 인한 요청인지 구분하는 플래그
    private var isUserInitiatedRequest = false

    // 토스트 메시지 중복 방지를 위한 변수들 추가
    private var lastToastMessage = ""
    private var lastToastTime = 0L

    // 경로 변경 리스너 인터페이스
    interface OnRouteChangeListener {
        fun onRouteReady(routes: List<NavigationRoute>)
        fun onRouteError(message: String)
    }

    // 경로 변경 리스너
    private var routeChangeListener: OnRouteChangeListener? = null

    fun setOrigin(origin: Point) {
        currentOrigin = origin

        // 출발지와 목적지가 모두 설정되었다면 경로 요청
        if (currentDestination != null) {
            requestRoute()
        }
    }

    fun setDestination(destination: Point) {
        Log.d("RouteManager", "Setting destination: ${destination.longitude()}, ${destination.latitude()}")

        // 목적지가 변경되었는지 확인
        val destinationChanged = currentDestination?.let { current ->
            val distance = calculateDistance(
                current.latitude(), current.longitude(),
                destination.latitude(), destination.longitude()
            )
            distance > Constants.DESTINATION_CHANGE_THRESHOLD
        } ?: true

        if (destinationChanged) {
            Log.d("RouteManager", "Destination changed, clearing cache and requesting new route")

            // 목적지가 변경되었으므로 캐시 클리어
            clearCachedRoutes()

            currentDestination = destination
            isUserInitiatedRequest = true // 사용자가 목적지를 설정한 경우

            // 현재 위치가 있는지 확인하고 경로 요청
            if (currentOrigin != null) {
                Log.d("RouteManager", "Origin available, requesting route for new destination")
                requestRoute()
            } else {
                Log.d("RouteManager", "Origin not available yet, waiting for location")
            }
        } else {
            Log.d("RouteManager", "Destination unchanged, keeping existing route")
        }
    }

    // 거리 계산 함수 추가
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

    fun setRouteChangeListener(listener: OnRouteChangeListener) {
        routeChangeListener = listener
    }

    fun requestRoute() {
        val origin = currentOrigin ?: return
        val destination = currentDestination ?: return

        // 1. 디바운싱 체크 (기존과 동일)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRouteRequestTime < Constants.ROUTE_REQUEST_DEBOUNCE_TIME) {
            Log.d("RouteManager", "Route request debounced")
            return
        }
        lastRouteRequestTime = currentTime

        // 2. 네트워크 연결 상태 확인
        val isNetworkAvailable = isNetworkConnected()

        if (!isNetworkAvailable) {
            Log.d("RouteManager", "Offline mode detected, attempting offline route calculation")

/*            // 오프라인 상태에서 캐시된 경로가 있으면 사용 (토스트 개선)
            if (useCachedRoutesIfAvailable()) {
                showDebouncedToast(
                    languageManager.getLocalizedString(
                        "오프라인 모드: 캐시된 경로 사용",
                        "Offline mode: Using cached route"
                    )
                )
                return
            }*/

            // 캐시된 경로가 없어도 오프라인 타일로 경로 계산 시도
            Log.d("RouteManager", "No cached routes, attempting offline route calculation with downloaded tiles")
        }

        // 3. 경로 요청 (온라인/오프라인 모두 동일하게 시도)
        performRouteRequest(origin, destination, isNetworkAvailable)
    }

    // 토스트 메시지 중복 방지 함수 추가
    private fun showDebouncedToast(message: String) {
        val currentTime = System.currentTimeMillis()

        // Constants에서 적절한 디바운싱 시간 선택
        val debounceTime = when {
            message.contains("오프라인") || message.contains("offline", true) ->
                Constants.OFFLINE_TOAST_DEBOUNCE_TIME
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

    // 자동 업데이트용 경로 요청 메서드 추가
    fun requestRouteAutoUpdate() {
        isUserInitiatedRequest = false // 자동 업데이트임을 표시
        requestRoute()
    }

    // 네트워크 연결 상태 확인
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 내비게이션 모드에 따른 프로필 반환
    private fun getNavigationProfile(): String {
        return when (languageManager.currentNavigationMode) {
            Constants.NAVIGATION_MODE_DRIVING -> DirectionsCriteria.PROFILE_DRIVING
            Constants.NAVIGATION_MODE_WALKING -> DirectionsCriteria.PROFILE_WALKING
            Constants.NAVIGATION_MODE_CYCLING -> DirectionsCriteria.PROFILE_CYCLING
            else -> DirectionsCriteria.PROFILE_DRIVING // 기본값
        }
    }

    private fun performRouteRequest(origin: Point, destination: Point, isOnline: Boolean) {
        val currentMode = languageManager.currentNavigationMode
        val modeDisplayName = languageManager.getCurrentNavigationModeDisplayName()

        Log.d("RouteManager", "Requesting $currentMode route (${if(isOnline) "online" else "offline"}) from: ${origin.longitude()},${origin.latitude()} to: ${destination.longitude()},${destination.latitude()}")

        val navigationProfile = getNavigationProfile()

        try {
            val routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions(navigationProfile)
                .coordinatesList(listOf(origin, destination))
                .layersList(listOf(mapboxNavigation.getZLevel(), null))
                .language(languageManager.currentLanguage)
                .steps(true)
                .build()

            Log.d("RouteManager", "Route options created for ${if(isOnline) "online" else "offline"} calculation with profile: $navigationProfile")

            mapboxNavigation.requestRoutes(
                routeOptions,
                object : NavigationRouterCallback {
                    override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                        Log.d("RouteManager", "Route request canceled")
                    }

                    override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                        Log.e("RouteManager", "Route request failed: ${reasons.firstOrNull()?.message}")

                        val message = if (isOnline) {
                            languageManager.getLocalizedString(
                                "$modeDisplayName 경로를 찾을 수 없습니다: ${reasons.firstOrNull()?.message}",
                                "Could not find $modeDisplayName route: ${reasons.firstOrNull()?.message}"
                            )
                        } else {
                            languageManager.getLocalizedString(
                                "오프라인 모드에서 $modeDisplayName 경로를 찾을 수 없습니다. 다운로드된 지역 범위를 확인해주세요.",
                                "Could not find $modeDisplayName route in offline mode. Please check downloaded area coverage."
                            )
                        }

                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        routeChangeListener?.onRouteError(message)
                    }

                    override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                        Log.d("RouteManager", "$modeDisplayName routes ready (${if(isOnline) "online" else "offline"}): ${routes.size} routes, origin: $routerOrigin")

                        // 성공적으로 받은 경로를 캐시에 저장
                        cacheRoutes(routes)

                        // 경로 설정
                        mapboxNavigation.setNavigationRoutes(routes)

                        // 사용자 액션일 때만 성공 메시지 표시 (토스트 개선)
                        if (isUserInitiatedRequest) {
                            val message = if (isOnline) {
                                languageManager.getLocalizedString(
                                    "$modeDisplayName 경로가 설정되었습니다",
                                    "$modeDisplayName route has been set"
                                )
                            } else {
                                languageManager.getLocalizedString(
                                    "오프라인 $modeDisplayName 경로가 설정되었습니다",
                                    "Offline $modeDisplayName route has been set"
                                )
                            }
                            showDebouncedToast(message)
                            isUserInitiatedRequest = false
                        }

                        // 리스너에게 경로 준비 알림
                        routeChangeListener?.onRouteReady(routes)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("RouteManager", "Error creating route options: ${e.message}", e)

            val message = languageManager.getLocalizedString(
                "경로 요청 중 오류가 발생했습니다: ${e.message}",
                "Error occurred while requesting route: ${e.message}"
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            routeChangeListener?.onRouteError(message)
        }
    }

    // 캐시된 경로 저장
    private fun cacheRoutes(routes: List<NavigationRoute>) {
        cachedRoutes = routes
        Log.d("RouteManager", "Routes cached: ${routes.size}")
    }

    // 캐시 클리어 함수 추가
    private fun clearCachedRoutes() {
        cachedRoutes = null
        Log.d("RouteManager", "Route cache cleared")
    }

    // 캐시된 경로 사용
    private fun useCachedRoutesIfAvailable(): Boolean {
        cachedRoutes?.let { routes ->
            if (routes.isNotEmpty()) {
                Log.d("RouteManager", "Using cached routes")
                mapboxNavigation.setNavigationRoutes(routes)
                routeChangeListener?.onRouteReady(routes)
                return true
            }
        }
        return false
    }

    fun clearRoutes() {
        mapboxNavigation.setNavigationRoutes(emptyList())
        currentDestination = null
        // 경로 클리어 시 캐시도 클리어
        clearCachedRoutes()
    }

    fun hasValidRoute(): Boolean {
        return currentOrigin != null && currentDestination != null
    }

    fun getOrigin(): Point? = currentOrigin

    fun getDestination(): Point? = currentDestination

    // 캐시된 경로가 있는지 확인하는 유틸리티 함수
    fun hasCachedRoutes(): Boolean {
        return cachedRoutes?.isNotEmpty() == true
    }

    // 현재 내비게이션 모드를 변경하고 경로를 다시 요청하는 메서드
    fun changeNavigationMode(newMode: String) {
        if (languageManager.changeNavigationMode(newMode)) {
            Log.d("RouteManager", "Navigation mode changed to: $newMode")

            // 내비게이션 모드 변경 시 캐시 클리어 (다른 모드의 경로이므로)
            clearCachedRoutes()

            // 현재 경로가 있다면 새로운 모드로 다시 요청
            if (hasValidRoute()) {
                Log.d("RouteManager", "Re-requesting route with new navigation mode")
                isUserInitiatedRequest = true // 사용자가 모드를 변경한 경우
                requestRoute()
            }
        }
    }

    // RouteManager.kt에 새로 추가할 메서드
    fun clearDestination() {
        Log.d("RouteManager", "Clearing destination only")

        // 목적지만 null로 설정 (출발지는 유지)
        currentDestination = null

        // 경로 캐시도 클리어
        clearCachedRoutes()

        // Mapbox Navigation의 경로 클리어
        mapboxNavigation.setNavigationRoutes(emptyList())

        Log.d("RouteManager", "Destination cleared, origin maintained")
    }
}