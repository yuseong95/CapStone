package com.quicinc.chatapp;


// 프로젝트 내부 클래스들

// Mapbox 관련

//whisper

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.quicinc.chatapp.map.MapInitializer
import com.quicinc.chatapp.map.OfflineTileManager
import com.quicinc.chatapp.GenieManager
import com.quicinc.chatapp.navigation.NavigationManager
import com.quicinc.chatapp.search.SearchManager
import com.quicinc.chatapp.search.SearchUI
import com.quicinc.chatapp.search.SearchButtonViewModel
import com.quicinc.chatapp.ui.LanguageManager
import com.quicinc.chatapp.ui.NavigationUI
import com.quicinc.chatapp.utils.PermissionHelper
import com.quicinc.chatapp.utils.Constants
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.example.capstone_whisper.WhisperService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import android.app.AlertDialog
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.util.concurrent.atomic.AtomicInteger

class Navi : ComponentActivity() {
    // 필요한 매니저 클래스들을 선언
    private lateinit var mapInitializer: MapInitializer
    private lateinit var navigationManager: NavigationManager
    private lateinit var offlineTileManager: OfflineTileManager
    private lateinit var navigationUI: NavigationUI
    private lateinit var languageManager: LanguageManager
    private lateinit var permissionHelper: PermissionHelper

    //whisperService 생성
    private var whisperService: WhisperService? = null
    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>

    //
    private lateinit var genieManager: GenieManager

    // 검색 관련 클래스
    private lateinit var searchManager: SearchManager
    private lateinit var searchUI: SearchUI

    // 새로운 UI 컴포넌트들
    private lateinit var searchContainer: RelativeLayout
    private lateinit var searchOverlay: LinearLayout
    private lateinit var bottomNavigationContainer: RelativeLayout
    private lateinit var navigationModePanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var mainActionButton: Button

    // 경로 정보 관련 컴포넌트들
    private lateinit var routeInfoPanel: LinearLayout
    private lateinit var routeDistanceText: TextView
    private lateinit var routeDurationText: TextView
    private lateinit var arrivalTimeText: TextView

    // 내비게이션 종료 확인 관련 컴포넌트들
    private lateinit var navigationExitOverlay: RelativeLayout
    private lateinit var navigationExitPanel: LinearLayout
    private lateinit var exitTimerText: TextView
    private lateinit var continueNavigationButton: Button
    private lateinit var exitNavigationButton: Button
    private lateinit var closeExitPanelButton: ImageButton

    // 네비게이션 탭들
    private lateinit var navHome: LinearLayout
    private lateinit var navNavigation: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var navAI: LinearLayout
    // 타이머 관련
    private var exitTimer: android.os.CountDownTimer? = null
    private var backPressedCount = 0
    private var lastBackPressedTime = 0L

    // 현재 선택된 탭
    private var currentTab = NavigationTab.HOME

    // 내부 상태 추적 변수들
    private var hasDestinationSet = false
    private var isNavigationActive = false

    // 토스트 중복 방지를 위한 변수들 추가
    private var lastToastMessage = ""
    private var lastToastTime = 0L


    // 명소 데이터 클래스
    data class Attraction(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    // 명소 목록을 저장할 변수
    private var attractionsList: List<Attraction> = emptyList()


    // 라마 경로
    private lateinit var htpExtConfigPath: String
    // 뒤로가기 콜백 추가
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackPress()
        }
    }

    enum class NavigationTab {
        HOME, NAVIGATION, SETTINGS ,AI
    }

    lateinit var searchButtonViewModel: SearchButtonViewModel

    // MapboxNavigation delegate
    private val mapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer attached")

                if (!::navigationManager.isInitialized) {
                    try {
                        // NavigationManager 초기화
                        navigationManager = NavigationManager(
                            this@Navi,
                            lifecycleScope,
                            mapInitializer.getMapView(),
                            mapInitializer,
                            languageManager,
                            navigationUI
                        )

                        // UI와 네비게이션 매니저 연결
                        navigationUI.setNavigationManager(navigationManager)

                        // 지도 클릭 리스너 설정
                        initializeMapClickListener()

                        // 검색 관련 초기화
                        initializeSearchComponents()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "NavigationManager 초기화 오류", e)
                    }
                }

                // MapboxNavigation 인스턴스를 NavigationManager에 전달
                navigationManager.onNavigationAttached(mapboxNavigation)
                navigationManager.startNetworkMonitoring()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                Log.d("MainActivity", "Navigation observer detached")
                if (::navigationManager.isInitialized) {
                    navigationManager.onNavigationDetached(mapboxNavigation)
                }
            }
        },
        onInitialize = {
            setupMapboxNavigation()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mainavi)
        htpExtConfigPath = intent.getStringExtra("htp_config_path") ?: run {
            Toast.makeText(this, "HTP 설정 경로가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // 뒤로가기 콜백 등록
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        // ViewModel 초기화
        searchButtonViewModel = ViewModelProvider(this)[SearchButtonViewModel::class.java]

        // UI 컴포넌트 초기화
        initializeUIComponents()

        // 1단계: Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        loadAttractionsData()
        // 2단계: TileStore 글로벌 설정
        val globalTileStore = MapInitializer.setupGlobalTileStore(this)
        Log.d("MainActivity", "Global TileStore configured before MapboxNavigationApp setup")

        // 3단계: MapboxNavigationApp 설정
        setupMapboxNavigation()

        // 4단계: 기본 매니저 초기화 (여기서 languageManager 초기화됨)
        initializeManagers()

        // 5단계: ViewModel 상태 관찰 설정 (languageManager 초기화 후로 이동)
        observeViewModelState()

        // 6단계: 새로운 UI 이벤트 설정
        setupNewUIEvents()

        // 권한 확인 및 요청
        permissionHelper.checkLocationPermissions()

        // 검색 컴포넌트 초기화 시도
        if (::navigationManager.isInitialized) {
            initializeSearchComponents()
        }

        // WhisperService 초기화 및 마이크 권한 요청 (porcupine 대기 포함)
        setupWhisperService()

        // 메인 액션 버튼 클릭 리스너
        mainActionButton.setOnClickListener {
            Log.d("MainActivity", "mainActionButton clicked")
            if (::navigationManager.isInitialized) {
                if (navigationManager.isNavigating()) {
                    // 내비게이션 중에는 이 버튼이 보이지 않아야 하지만, 혹시 모를 경우를 대비
                    Log.w("MainActivity", "Main action button clicked during navigation - this should not happen")
                } else {
                    navigationManager.startNavigation()
                }
            }
        }



    }
    private fun loadAttractionsData() {
        try {
            // assets 폴더에서 파일 읽기
            val inputStream = assets.open("korea_attractions.txt")
            val fileContent = inputStream.bufferedReader().use { it.readText() }

            attractionsList = fileContent.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        val parts = line.split(",").map { it.trim() }
                        if (parts.size >= 3) {
                            Attraction(
                                name = parts[0],
                                latitude = parts[1].toDouble(),
                                longitude = parts[2].toDouble()
                            )
                        } else null
                    } catch (e: Exception) {
                        Log.e("AttractionsData", "파싱 오류: $line", e)
                        null
                    }
                }

            Log.d("AttractionsData", "명소 데이터 로드 완료: ${attractionsList.size}개")

        } catch (e: Exception) {
            Log.e("AttractionsData", "명소 데이터 로드 실패", e)
            attractionsList = emptyList()
        }
    }
    private fun showSafeToast(message: String, length: Int = Toast.LENGTH_LONG) {
        try {
            runOnUiThread {
                Toast.makeText(this@Navi, message, length).show()
            }
        } catch (e: Exception) {
            Log.e("SafeToast", "Toast 표시 중 오류: ${e.message}", e)
        }
    }
    /*whisper-------------------------------------------*/
    private fun setupWhisperService() {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startWhisperService()
            } else {
                Toast.makeText(this, "🎤 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startWhisperService()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // porcupine 사용 시 호출 함수
    private fun startWhisperService() {
        whisperService = WhisperService(this) { result ->
            runOnUiThread {
                //Toast.makeText(this, "📝 인식 결과: $result", Toast.LENGTH_LONG).show()
                Log.d("WhisperResult", result)


                //
                GenieManager.initialize(this,"llama3_2_3b",htpExtConfigPath)
                GenieManager.processPrompt(result) { result ->
                    Log.d("Genie", "응답: $result")
                    // Toast.makeText(this, "라마: $result", Toast.LENGTH_LONG).show()
                    // 1) ',' 로 자르고 양옆 공백 제거
                    val hasKorean = result.contains(Regex("[가-힣]"))

                    if (!hasKorean || !result.contains(",")) {
                        Log.w("TAG", "한글이 포함되지 않은 응답: $result")
                        showSafeToast("다시 한번 말씀해 주세요")
                    }
                    else {
                        val parts = result.split(",").map { it.trim() }
                        if (parts.size < 3) {
                            Log.w("TAG", "응답 형식이 올바르지 않습니다. 받은 응답: $result")
                            showSafeToast("다시 한번 말씀해 주세요")
                        }
                        else if (parts.size == 3) {
                            val city = parts[0]
                            val latitudeStr = parts[1].trim()
                            val longitudeStr = parts[2].trim()
                            // 방법 2: 직접 변환해서 검증
                            // 방법 1: 간단한 문자 검증
                            if (!containsOnlyNumbersAndDot(latitudeStr) || !containsOnlyNumbersAndDot(
                                    longitudeStr
                                )
                            ) {
                                Log.w(
                                    "TAG",
                                    "위도/경도에 숫자가 아닌 문자 포함: 위도='$latitudeStr', 경도='$longitudeStr'"
                                )
                                showSafeToast("다시 한번 말씀해 주세요")

                            }
                            else {
                                val latitude = latitudeStr.toDouble()
                                val longitude = longitudeStr.toDouble()
                                Log.d("TAG", "도시: $city, 위도: $latitude, 경도: $longitude")

                                if (city.isNotEmpty()) {
                                    searchAndSetDestination(city)
                                }
                            }
                        }

                    }
                }

            }
        }
        whisperService?.start()
    }

    // 버튼 클릭 시 호출할 함수 버튼 -> onclick식 호출됨
    private fun startWhisperServiceTestWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startWhisperServiceTest()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // 또는 더 간단한 검증 함수
    private fun containsOnlyNumbersAndDot(str: String): Boolean {
        return str.all { it.isDigit() || it == '.' || it == '-' }
    }
    // 버튼에서 사용하는 위스퍼
    private fun startWhisperServiceTest() {
        Log.d("WhisperTest", "🔊 detect_voice (테스트용)")

        whisperService = WhisperService(this) { result ->
            runOnUiThread {
                //Toast.makeText(this, "📝 인식 결과: $result", Toast.LENGTH_LONG).show()
                Log.d("WhisperResult", result)


                //
                GenieManager.initialize(this,"llama3_2_3b",htpExtConfigPath)
                GenieManager.processPrompt(result) { result ->
                    Log.d("Genie","응답: $result")
                    // Toast.makeText(this, "라마: $result", Toast.LENGTH_LONG).show()
                    // 1) ',' 로 자르고 양옆 공백 제거
                    if (result.length > 100) {
                        Log.w("TAG", "응답이 너무 깁니다 (${result.length}자): ${result.take(50)}...")
                        showSafeToast("다시 한번 말씀해 주세요")

                    }
                    val hasKorean = result.contains(Regex("[가-힣]"))
                    if (!hasKorean && !result.contains(",")) {
                        Log.w("TAG", "한글이 포함되지 않은 응답: $result")
                        showSafeToast("다시 한번 말씀해 주세요")

                    }
                    if (!result.contains(",")) {
                        Log.w("TAG", "콤마가 없는 응답 (문장형): $result")
                        showSafeToast("다시 한번 말씀해 주세요")

                    }
                    val parts = result.split(",").map { it.trim() }
                    if (parts.size < 3) {
                        Log.w("TAG", "응답 형식이 올바르지 않습니다. 받은 응답: $result")
                        showSafeToast("다시 한번 말씀해 주세요")

                    }
                    val city = parts[0]
                    val latitudeStr = parts[1].trim()
                    val longitudeStr = parts[2].trim()
                    // 방법 2: 직접 변환해서 검증
                    // 방법 1: 간단한 문자 검증
                    if (!containsOnlyNumbersAndDot(latitudeStr) || !containsOnlyNumbersAndDot(longitudeStr)) {
                        Log.w("TAG", "위도/경도에 숫자가 아닌 문자 포함: 위도='$latitudeStr', 경도='$longitudeStr'")
                        showSafeToast("다시 한번 말씀해 주세요")

                    }
                    val latitude = latitudeStr.toDouble()
                    val longitude = longitudeStr.toDouble()
                    Log.d("TAG", "도시: $city, 위도: $latitude, 경도: $longitude")

                    if (city.isNotEmpty()) {
                        searchAndSetDestination(city)
                    }

                }

            }
        }
        whisperService?.startWithoutWakeWord()
    }
    //-----------------------------------------------
    // whisper search test




    // 검색하고 첫 번째 결과를 바로 목적지로 설정 (로컬 데이터 우선)
    private fun searchAndSetDestination(city: String) {
        Log.d("SearchUI", "🔍 음성으로 인식된 도시 검색: $city")

        // 1. 먼저 로컬 명소 데이터에서 찾기
        val matchingAttraction = findMatchingAttraction(city)

        if (matchingAttraction != null) {
            // 로컬 데이터에서 발견 - 바로 목적지 설정
            Log.d("SearchUI", "✅ 로컬 데이터에서 발견: ${matchingAttraction.name}")
            setDestinationFromAttraction(matchingAttraction)
            return
        }

        // 2. 로컬 데이터에 없으면 기존 검색 방식 사용
        Log.d("SearchUI", "로컬 데이터에 없음, 온라인 검색 시작")
        performOnlineSearch(city)
    }

    // 로컬 명소 데이터에서 일치하는 항목 찾기
    private fun findMatchingAttraction(query: String): Attraction? {
        if (attractionsList.isEmpty()) {
            Log.w("SearchUI", "명소 데이터가 로드되지 않았습니다")
            return null
        }

        val searchQuery = query.trim().lowercase()

        // 1. 완전 일치 우선
        var match = attractionsList.find { attraction ->
            attraction.name.lowercase() == searchQuery
        }

        if (match != null) {
            Log.d("SearchUI", "완전 일치 발견: ${match.name}")
            return match
        }

        // 2. 부분 일치 (명소 이름에 검색어가 포함된 경우)
        match = attractionsList.find { attraction ->
            attraction.name.lowercase().contains(searchQuery)
        }

        if (match != null) {
            Log.d("SearchUI", "부분 일치 발견: ${match.name}")
            return match
        }

        // 3. 검색어가 명소 이름에 포함된 경우 (반대 방향)
        match = attractionsList.find { attraction ->
            searchQuery.contains(attraction.name.lowercase())
        }

        if (match != null) {
            Log.d("SearchUI", "역 부분 일치 발견: ${match.name}")
            return match
        }

        Log.d("SearchUI", "로컬 데이터에서 '$query'와 일치하는 명소 없음")
        return null
    }

    // 크래시 방지를 위한 안전한 목적지 설정
    private fun setDestinationFromAttraction(attraction: Attraction) {
        // 반드시 UI 스레드에서 실행
        runOnUiThread {
            try {
                Log.d("Navigation", "🎯 목적지 설정 시작: ${attraction.name}")
                Log.d("Navigation", "좌표: lat=${attraction.latitude}, lng=${attraction.longitude}")

                // Mapbox Point 객체 생성 (경도, 위도 순서 주의!)
                val point = Point.fromLngLat(attraction.longitude, attraction.latitude)

                // NavigationManager가 초기화되었는지 확인
                if (!::navigationManager.isInitialized) {
                    Log.e("Navigation", "NavigationManager가 초기화되지 않았습니다")
                    Toast.makeText(this@Navi, "❌ 내비게이션 시스템 오류", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                // 목적지 설정
                navigationManager.setDestination(point)

                Log.d("Navigation", "🎯 로컬 데이터로 목적지 설정 완료: ${attraction.name}")
                Toast.makeText(this@Navi, "목적지 설정: ${attraction.name}", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("Navigation", "목적지 설정 중 오류: ${e.message}", e)
                Log.e("Navigation", "스택 트레이스: ${e.stackTraceToString()}")
                Toast.makeText(this@Navi, "❌ 목적지 설정 실패: ${attraction.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    // 3. 온라인 검색 함수 수정 (기존 코드 교체)
    private fun performOnlineSearch(city: String) {
        // UI 스레드에서만 실행 보장
        if (!Thread.currentThread().equals(Looper.getMainLooper().thread)) {
            runOnUiThread {
                performOnlineSearch(city)
            }
            return
        }

        Log.d("SearchUI", "🔍 온라인 검색 시작: $city")

        try {
            val searchOptions = SearchOptions.Builder()
                .countries(listOf(IsoCountryCode.SOUTH_KOREA))
                .languages(listOf(
                    if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
                ))
                .limit(1)
                .build()

            // SearchBox 엔진으로 검색 (UI 스레드에서 안전하게)
            searchManager.searchBoxEngine.search(
                query = city,
                options = searchOptions,
                callback = object : SearchSuggestionsCallback {
                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        // 콜백도 UI 스레드에서 실행 보장
                        runOnUiThread {
                            Log.d("SearchUI", "SearchBox suggestions: ${suggestions.size}")

                            if (suggestions.isNotEmpty()) {
                                val firstResult = suggestions[0]
                                setDestinationFromSuggestion(firstResult)
                            } else {
                                Log.d("SearchUI", "SearchBox 결과 없음, Geocoding으로 재시도")
                                fallbackToGeocoding(city)
                            }
                        }
                    }

                    override fun onError(e: Exception) {
                        runOnUiThread {
                            Log.e("SearchUI", "SearchBox error: ${e.message}", e)
                            fallbackToGeocoding(city)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SearchUI", "performOnlineSearch 오류: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "❌ 검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 4. Geocoding 검색 함수 수정 (기존 코드 교체)
    private fun fallbackToGeocoding(city: String) {
        // UI 스레드에서만 실행 보장
        if (!Thread.currentThread().equals(Looper.getMainLooper().thread)) {
            runOnUiThread {
                fallbackToGeocoding(city)
            }
            return
        }

        Log.d("SearchUI", "🔍 Geocoding 검색 시작: $city")

        try {
            val searchOptions = SearchOptions.Builder()
                .countries(listOf(IsoCountryCode.SOUTH_KOREA))
                .languages(listOf(
                    if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
                ))
                .limit(1)
                .build()

            searchManager.geocodingEngine.search(
                query = city,
                options = searchOptions,
                callback = object : SearchSuggestionsCallback {
                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        runOnUiThread {
                            Log.d("SearchUI", "Geocoding suggestions: ${suggestions.size}")

                            if (suggestions.isNotEmpty()) {
                                val firstResult = suggestions[0]
                                setDestinationFromSuggestion(firstResult)
                            } else {
                                Toast.makeText(this@Navi, "❌ '$city'를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onError(e: Exception) {
                        runOnUiThread {
                            Log.e("SearchUI", "Geocoding error: ${e.message}", e)
                            Toast.makeText(this@Navi, "❌ 검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SearchUI", "fallbackToGeocoding 오류: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "❌ 검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 5. 온라인 검색 결과 목적지 설정 수정 (기존 코드 교체)
    private fun setDestinationFromSuggestion(suggestion: SearchSuggestion) {
        // UI 스레드에서만 실행 보장
        if (!Thread.currentThread().equals(Looper.getMainLooper().thread)) {
            runOnUiThread {
                setDestinationFromSuggestion(suggestion)
            }
            return
        }

        try {
            Log.d("Navigation", "🎯 목적지 설정 시작: ${suggestion.name}")

            val point = suggestion.coordinate
            if (point != null) {
                // NavigationManager 초기화 확인
                if (!::navigationManager.isInitialized) {
                    Log.e("Navigation", "NavigationManager가 초기화되지 않았습니다")
                    Toast.makeText(this, "❌ 내비게이션 시스템 오류", Toast.LENGTH_SHORT).show()
                    return
                }

                Log.d("Navigation", "좌표: lng=${point.longitude()}, lat=${point.latitude()}")

                navigationManager.setDestination(point)
                Log.d("Navigation", "🎯 온라인 검색으로 목적지 설정 완료: ${suggestion.name}")
                Toast.makeText(this, "목적지 설정: ${suggestion.name}", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("Navigation", "좌표 정보가 없습니다: ${suggestion.name}")
                Toast.makeText(this, "❌ 위치 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("Navigation", "목적지 설정 중 오류: ${e.message}", e)
            Log.e("Navigation", "스택 트레이스: ${e.stackTraceToString()}")
            Toast.makeText(this, "❌ 목적지 설정 실패", Toast.LENGTH_SHORT).show()
        }
    }

    //-----------------------------------------------
    // 권한 확인용
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestMicrophonePermission() {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startWhisperService()
            } else {
                Toast.makeText(this, "🎤 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    /*------------------------------------*/
    private fun initializeUIComponents() {
        // 기존 UI 컴포넌트들 참조
        searchContainer = findViewById(R.id.searchContainer)
        searchOverlay = findViewById(R.id.searchOverlay)
        bottomNavigationContainer = findViewById(R.id.bottomNavigationContainer)
        navigationModePanel = findViewById(R.id.navigationModePanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        mainActionButton = findViewById(R.id.mainActionButton)

        // 새로 추가된 버튼 컨테이너 참조
        val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)

        // 경로 정보 관련 컴포넌트들
        routeInfoPanel = findViewById(R.id.routeInfoPanel)
        routeDistanceText = findViewById(R.id.routeDistanceText)
        routeDurationText = findViewById(R.id.routeDurationText)
        arrivalTimeText = findViewById(R.id.arrivalTimeText)

        // 내비게이션 종료 확인 관련 컴포넌트들
        navigationExitOverlay = findViewById(R.id.navigationExitOverlay)
        navigationExitPanel = findViewById(R.id.navigationExitPanel)
        exitTimerText = findViewById(R.id.exitTimerText)
        continueNavigationButton = findViewById(R.id.continueNavigationButton)
        exitNavigationButton = findViewById(R.id.exitNavigationButton)
        closeExitPanelButton = findViewById(R.id.closeExitPanelButton)

        // 네비게이션 탭들
        navAI = findViewById(R.id.navAI)
        navHome = findViewById(R.id.navHome)
        navNavigation = findViewById(R.id.navNavigation)
        navSettings = findViewById(R.id.navSettings)

        //whisper test 버튼
        val testWhisperButton = findViewById<FloatingActionButton>(R.id.testWhisperButton)
        // onclick 시 권한 확인 및 녹음 시작
        testWhisperButton.setOnClickListener {
            Log.d("MainActivity", "🎤 테스트 Whisper 버튼 클릭됨")
            if (checkMicrophonePermission()) {
                startWhisperServiceTestWithPermissionCheck()
            } else {
                requestMicrophonePermission()
            }
        }

        // 초기 상태 설정
        mainActionButton.visibility = View.GONE
        buttonContainer.visibility = View.VISIBLE  // 컨테이너는 항상 보이도록
        bottomNavigationContainer.visibility = View.VISIBLE
        searchOverlay.visibility = View.GONE
        navigationModePanel.visibility = View.GONE
        settingsPanel.visibility = View.GONE
        routeInfoPanel.visibility = View.GONE
        navigationExitOverlay.visibility = View.GONE

        // 내비게이션 종료 패널 이벤트 설정
        setupNavigationExitPanel()

        // 기본 탭 선택
        selectTab(NavigationTab.HOME)

        // 디버깅: 초기 레이아웃 정보
        Log.d("MainActivity", "Initial UI state - bottomContainer: ${bottomNavigationContainer.visibility}")
        Log.d("MainActivity", "buttonContainer: ${buttonContainer.visibility}")
        Log.d("MainActivity", "mainButton: ${mainActionButton.visibility}")
    }

    // 뒤로가기 처리 로직
    private fun handleBackPress() {
        when {
            // 1. 내비게이션 종료 확인 패널이 열려있으면 패널 닫기
            navigationExitOverlay.visibility == View.VISIBLE -> {
                Log.d("MainActivity", "Back pressed - closing navigation exit panel")
                hideNavigationExitPanel()
            }

            // 2. 검색 UI가 열려있으면 검색 UI 닫기
            searchButtonViewModel.isSearchUIVisible.value -> {
                Log.d("MainActivity", "Back pressed - closing search UI")
                searchButtonViewModel.closeSearchUI()
            }

            // 3. 설정 패널이 열려있으면 설정 패널 닫기
            settingsPanel.visibility == View.VISIBLE -> {
                Log.d("MainActivity", "Back pressed - closing settings panel")
                selectTab(NavigationTab.HOME)
            }

            // 4. 내비게이션 모드 패널이 열려있으면 패널 닫기
            navigationModePanel.visibility == View.VISIBLE -> {
                Log.d("MainActivity", "Back pressed - closing navigation mode panel")
                selectTab(NavigationTab.HOME)
            }

            // 5. 내비게이션이 활성화되어 있으면 종료 확인 패널 표시 또는 이중 뒤로가기로 종료
            ::navigationManager.isInitialized && navigationManager.isNavigating() -> {
                handleNavigationBackPress()
            }
            // 새로 추가: 목적지가 설정되어 있지만 내비게이션이 시작되지 않은 상태
            hasDestinationSet && !isNavigationActive -> {
                Log.d("MainActivity", "Back pressed - clearing destination")
                clearDestination()
            }
            // 6. 모든 것이 닫혀있으면 앱 종료 확인
            else -> {
                Log.d("MainActivity", "Back pressed - showing exit dialog")
                showExitDialog()
            }
        }
    }
    private fun clearDestination() {
        Log.d("MainActivity", "Clearing destination and resetting to initial state")

        if (::navigationManager.isInitialized) {
            // NavigationManager를 통해 목적지 해제
            navigationManager.clearDestination()
        }

        // 내부 상태 초기화
        hasDestinationSet = false

        // ViewModel 상태 초기화
        searchButtonViewModel.setHasDestination(false)

        // UI 업데이트
        runOnUiThread {
            // 메인 액션 버튼 숨기기
            mainActionButton.visibility = View.GONE

            // 경로 정보 패널 숨기기
            routeInfoPanel.visibility = View.GONE

            Log.d("MainActivity", "Destination cleared, UI reset to initial state")
        }
    }

    // 내비게이션 중 뒤로가기 처리
    private fun handleNavigationBackPress() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastBackPressedTime < 2000) {
            // 2초 내에 두 번째 뒤로가기 - 즉시 내비게이션 종료
            backPressedCount++
            if (backPressedCount >= 2) {
                Log.d("MainActivity", "Double back press - canceling navigation immediately")
                if (::navigationManager.isInitialized) {
                    navigationManager.cancelNavigation()
                }
                backPressedCount = 0
                return
            }
        } else {
            // 첫 번째 뒤로가기 또는 시간이 지난 후 - 종료 확인 패널 표시
            backPressedCount = 1
            showNavigationExitPanel()
        }

        lastBackPressedTime = currentTime
    }

    // 내비게이션 종료 패널 설정
    private fun setupNavigationExitPanel() {
        // 계속하기 버튼
        continueNavigationButton.setOnClickListener {
            Log.d("MainActivity", "Continue navigation button clicked")
            hideNavigationExitPanel()
            backPressedCount = 0
        }

        // 종료 버튼
        exitNavigationButton.setOnClickListener {
            Log.d("MainActivity", "Exit navigation button clicked")
            if (::navigationManager.isInitialized) {
                navigationManager.cancelNavigation()
            }
            hideNavigationExitPanel()
            backPressedCount = 0
        }

        // X 버튼
        closeExitPanelButton.setOnClickListener {
            Log.d("MainActivity", "Close exit panel button clicked")
            hideNavigationExitPanel()
            backPressedCount = 0
        }

        // 오버레이 클릭으로 패널 닫기
        navigationExitOverlay.setOnClickListener { view ->
            if (view == navigationExitOverlay) {
                Log.d("MainActivity", "Exit panel overlay clicked")
                hideNavigationExitPanel()
                backPressedCount = 0
            }
        }

        // 패널 자체 클릭 시에는 닫히지 않도록
        navigationExitPanel.setOnClickListener {
            // 이벤트 소비만 하고 아무것도 하지 않음
        }
    }

    // 내비게이션 종료 패널 표시
    private fun showNavigationExitPanel() {
        Log.d("MainActivity", "Showing navigation exit panel")

        navigationExitOverlay.visibility = View.VISIBLE

        // 슬라이드 업 애니메이션
        val animator = ObjectAnimator.ofFloat(navigationExitPanel, "translationY", 400f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.start()

        // 10초 타이머 시작
        startExitTimer()
    }

    // 내비게이션 종료 패널 숨기기
    private fun hideNavigationExitPanel() {
        Log.d("MainActivity", "Hiding navigation exit panel")

        stopExitTimer()

        // 슬라이드 다운 애니메이션
        val animator = ObjectAnimator.ofFloat(navigationExitPanel, "translationY", 0f, 400f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            if (animation.animatedFraction == 1.0f) {
                navigationExitOverlay.visibility = View.GONE
            }
        }
        animator.start()
    }

    // 10초 자동 종료 타이머 시작
    private fun startExitTimer() {
        stopExitTimer() // 기존 타이머가 있으면 중지

        exitTimer = object : android.os.CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                exitTimerText.text = secondsLeft.toString()
            }

            override fun onFinish() {
                Log.d("MainActivity", "Exit timer finished - auto canceling navigation")

                // 내비게이션 자동 종료
                if (::navigationManager.isInitialized) {
                    navigationManager.cancelNavigation()
                }

                hideNavigationExitPanel()
                backPressedCount = 0
            }
        }.start()
    }

    // 타이머 중지
    private fun stopExitTimer() {
        exitTimer?.cancel()
        exitTimer = null
        exitTimerText.text = "10"
    }

    // 앱 종료 확인 다이얼로그
    private fun showExitDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getLocalizedString("앱 종료", "Exit App"))
            .setMessage(languageManager.getLocalizedString(
                "앱을 종료하시겠습니까?",
                "Do you want to exit the app?"
            ))
            .setPositiveButton(languageManager.getLocalizedString("종료", "Exit")) { _, _ ->
                finish()
            }
            .setNegativeButton(languageManager.getLocalizedString("취소", "Cancel"), null)
            .show()
    }

    private fun setupNewUIEvents() {
        // 검색 컨테이너 클릭 이벤트
        searchContainer.setOnClickListener {
            Log.d("MainActivity", "Search container clicked")
            if (::searchUI.isInitialized) {
                searchButtonViewModel.openSearchUI()
            }
        }


        // 네비게이션 탭 클릭 이벤트들
        navAI.setOnClickListener {
            selectTab(NavigationTab.AI)
        }
        navHome.setOnClickListener {
            selectTab(NavigationTab.HOME)
        }

        navNavigation.setOnClickListener {
            selectTab(NavigationTab.NAVIGATION)
        }

        navSettings.setOnClickListener {
            selectTab(NavigationTab.SETTINGS)
        }

        // 내비게이션 모드 패널 옵션들
        setupNavigationModeOptions()

        // 설정 패널 옵션들
        setupSettingsOptions()
    }

    private fun setupNavigationModeOptions() {
        val drivingOption = findViewById<LinearLayout>(R.id.drivingModeOption)
        val walkingOption = findViewById<LinearLayout>(R.id.walkingModeOption)
        val cyclingOption = findViewById<LinearLayout>(R.id.cyclingModeOption)

        drivingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_DRIVING)
        }

        walkingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_WALKING)
        }

        cyclingOption.setOnClickListener {
            changeNavigationMode(Constants.NAVIGATION_MODE_CYCLING)
        }
    }

    private fun setupSettingsOptions() {
        val koreanOption = findViewById<RelativeLayout>(R.id.koreanLanguageOption)
        val englishOption = findViewById<RelativeLayout>(R.id.englishLanguageOption)
        val offlineMapOption = findViewById<RelativeLayout>(R.id.offlineMapOption)
        val appInfoOption = findViewById<RelativeLayout>(R.id.appInfoOption)

        koreanOption.setOnClickListener {
            changeLanguage(Constants.LANG_KOREAN)
        }

        englishOption.setOnClickListener {
            changeLanguage(Constants.LANG_ENGLISH)
        }

        offlineMapOption.setOnClickListener {
            // 오프라인 지도 관리 액티비티 시작
            val intent = android.content.Intent(this, com.quicinc.chatapp.offline.OfflineMapActivity::class.java)
            startActivity(intent)
        }

        appInfoOption.setOnClickListener {
            val appInfoMessage = """
        📱 OffqualBot 앱
        
        👨‍💻 개발자: 나유성,송상훈,유도현,강기영
        📋 버전: 1.0
        📅 빌드 날짜: 2025년 5월 29일
        
        © 2025 Hansung 20
    """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("앱 정보")
                .setMessage(appInfoMessage)
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                }
                .setIcon(R.drawable.ic_info) // 선택사항: 아이콘 추가
                .show()
        }
    }

    private fun changeNavigationMode(mode: String) {
        if (::languageManager.isInitialized && languageManager.changeNavigationMode(mode)) {
            updateNavigationModeUI(mode)

            if (::navigationManager.isInitialized) {
                navigationManager.getRouteManager().changeNavigationMode(mode)
            }

            val modeDisplayName = languageManager.getNavigationModeDisplayName(mode)
            Toast.makeText(this, "내비게이션 모드: $modeDisplayName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeLanguage(language: String) {
        if (::languageManager.isInitialized && languageManager.changeLanguage(language)) {
            updateLanguageUI(language)

            if (::navigationManager.isInitialized) {
                navigationManager.updateLanguage(language)
            }

/*            val message = if (language == Constants.LANG_KOREAN) {
                "언어가 한국어로 변경되었습니다"
            } else {
                "Language changed to English"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()*/
        }
    }

    private fun updateNavigationModeUI(mode: String) {
        val drivingCheck = findViewById<View>(R.id.drivingCheck)
        val walkingCheck = findViewById<View>(R.id.walkingCheck)
        val cyclingCheck = findViewById<View>(R.id.cyclingCheck)

        // 모든 체크 숨기기
        drivingCheck.visibility = View.GONE
        walkingCheck.visibility = View.GONE
        cyclingCheck.visibility = View.GONE

        // 선택된 모드만 체크 표시
        when (mode) {
            Constants.NAVIGATION_MODE_DRIVING -> drivingCheck.visibility = View.VISIBLE
            Constants.NAVIGATION_MODE_WALKING -> walkingCheck.visibility = View.VISIBLE
            Constants.NAVIGATION_MODE_CYCLING -> cyclingCheck.visibility = View.VISIBLE
        }
    }

    private fun updateLanguageUI(language: String) {
        val koreanCheck = findViewById<View>(R.id.koreanCheck)
        val englishCheck = findViewById<View>(R.id.englishCheck)

        if (language == Constants.LANG_KOREAN) {
            koreanCheck.visibility = View.VISIBLE
            englishCheck.visibility = View.GONE
        } else {
            koreanCheck.visibility = View.GONE
            englishCheck.visibility = View.VISIBLE
        }

        // UI 텍스트들 업데이트
        updateUITexts()
    }

    private fun updateUITexts() {
        if (!::languageManager.isInitialized) return

        val searchHint = findViewById<TextView>(R.id.searchHint)
        searchHint.text = languageManager.getLocalizedString("목적지 검색", "Search destination")

        // 메인 액션 버튼 텍스트 업데이트
        mainActionButton.text = if (isNavigationActive) {
            languageManager.getLocalizedString("취소", "Cancel")
        } else {
            languageManager.getLocalizedString("시작", "Start")
        }
    }

    private fun selectTab(tab: NavigationTab) {
        // 이전 패널들 숨기기
        hideAllPanels()

        // 모든 탭 비활성화
        updateTabAppearance(navAI, false)
        updateTabAppearance(navHome, false)
        updateTabAppearance(navNavigation, false)
        updateTabAppearance(navSettings, false)

        // 선택된 탭 활성화 및 해당 패널 표시
        currentTab = tab
        when (tab) {
            NavigationTab.AI -> {
                updateTabAppearance(navAI, true)

                val intent = Intent(this@Navi, Conversation::class.java).apply {
                    putExtra(Conversation.cConversationActivityKeyHtpConfig, htpExtConfigPath.toString())
                    putExtra(Conversation.cConversationActivityKeyModelName, "llama3_2_3b")
                }
                startActivity(intent)
            }
            NavigationTab.HOME -> {
                updateTabAppearance(navHome, true)
                // 홈 탭에서는 추가 패널이 없음
            }
            NavigationTab.NAVIGATION -> {
                updateTabAppearance(navNavigation, true)
                showPanel(navigationModePanel)

                val handleBar2 = navigationModePanel.findViewById<View>(R.id.handleBar2)
                handleBar2.setOnClickListener {
                    hidePanel(navigationModePanel)
                }
            }
            NavigationTab.SETTINGS -> {
                updateTabAppearance(navSettings, true)
                showPanel(settingsPanel)

                val handleBar = settingsPanel.findViewById<View>(R.id.handleBar)
                handleBar.setOnClickListener {
                    hidePanel(settingsPanel)
                }
            }
        }
    }

    private fun updateTabAppearance(tabView: LinearLayout, isActive: Boolean) {
        val iconView = tabView.getChildAt(0) as android.widget.ImageView
        val textView = tabView.getChildAt(1) as TextView

        if (isActive) {
            iconView.setColorFilter(getColor(R.color.nav_icon_active))
            textView.setTextColor(getColor(R.color.nav_text_active))
        } else {
            iconView.setColorFilter(getColor(R.color.nav_icon_inactive))
            textView.setTextColor(getColor(R.color.nav_text_inactive))
        }
    }

    private fun hideAllPanels() {
        hidePanel(navigationModePanel)
        hidePanel(settingsPanel)
    }

    private fun showPanel(panel: LinearLayout) {
        panel.visibility = View.VISIBLE

        // 슬라이드 업 애니메이션
        val animator = ObjectAnimator.ofFloat(panel, "translationY", 400f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    private fun hidePanel(panel: LinearLayout) {
        if (panel.visibility == View.VISIBLE) {
            // 슬라이드 다운 애니메이션
            val animator = ObjectAnimator.ofFloat(panel, "translationY", 0f, 400f)
            animator.duration = 300
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                if (animation.animatedFraction == 1.0f) {
                    panel.visibility = View.GONE
                }
            }
            animator.start()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            // 검색 UI 가시성 상태 관찰
            searchButtonViewModel.isSearchUIVisible.collectLatest { isVisible ->
                Log.d("MainActivity", "Search UI visibility changed: $isVisible")
                if (::searchUI.isInitialized) {
                    if (isVisible) {
                        searchUI.showSearchUI()
                        searchOverlay.visibility = View.VISIBLE
                    } else {
                        searchUI.hideSearchUI()
                        searchOverlay.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            // 내비게이션 상태 관찰
            searchButtonViewModel.navigationActive.collectLatest { isActive ->
                Log.d("MainActivity", "Navigation active state changed: $isActive")
                isNavigationActive = isActive  // 내부 상태 업데이트
                updateNavigationUI(isActive)
            }
        }

        //목적지 상태 관찰 개선
        lifecycleScope.launch {
            searchButtonViewModel.hasDestination.collectLatest { hasDestination ->
                Log.d("MainActivity", "ViewModel destination state changed: $hasDestination")
                hasDestinationSet = hasDestination  // 내부 상태 업데이트

                // 내비게이션이 활성화되지 않은 상태에서만 버튼 가시성 업데이트
                if (!isNavigationActive) {
                    updateMainActionButtonVisibility()
                }
            }
        }
    }

    // 메인 액션 버튼 가시성만 업데이트
    private fun updateMainActionButtonVisibility() {
        Log.d("MainActivity", "updateMainActionButtonVisibility - hasDestination: $hasDestinationSet, isNavigating: $isNavigationActive")

        runOnUiThread {
            try {
                if (!isNavigationActive && hasDestinationSet) {
                    bottomNavigationContainer.visibility = View.VISIBLE
                    mainActionButton.visibility = View.VISIBLE
                    mainActionButton.text = languageManager.getLocalizedString("내비게이션 시작", "Start Navigation")
                    mainActionButton.isEnabled = true

                    Log.d("MainActivity", "✅ Showing start button - destination available")
                } else if (!isNavigationActive) {
                    mainActionButton.visibility = View.GONE
                    Log.d("MainActivity", "❌ Hiding start button - no destination")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating main action button visibility", e)
            }
        }
    }

    // 내비게이션 상태 업데이트 (토스트 개선)
    private fun updateNavigationUI(isNavigating: Boolean) {
        if (!::languageManager.isInitialized) {
            Log.w("MainActivity", "updateNavigationUI called before languageManager initialization")
            return
        }

        Log.d("MainActivity", "updateNavigationUI called - isNavigating: $isNavigating")

        runOnUiThread {
            if (isNavigating) {
                // 내비게이션 시작 시 UI 숨기기
                Log.d("MainActivity", "Navigation started - hiding UI controls")

                // 1. 메인 액션 버튼 숨기기
                mainActionButton.visibility = View.GONE

                // 2. 하단 내비게이션 바 숨기기
                bottomNavigationContainer.visibility = View.GONE

                // 3. 상단 경로 정보 패널 숨기기 (중복 정보 제거)
                routeInfoPanel.visibility = View.GONE

                // 4. 검색 컨테이너도 숨기기 (내비게이션 중에는 검색 비활성화)
                searchContainer.visibility = View.GONE

                // 내비게이션 시작 토스트
                showDebouncedToast(languageManager.getLocalizedString(
                    "내비게이션이 시작되었습니다",
                    "Navigation started"
                ))

                Log.d("MainActivity", "Navigation started - all UI controls hidden")

            } else {
                // 내비게이션 종료 시 UI 복원
                Log.d("MainActivity", "Navigation ended - restoring UI controls")

                // 1. 검색 컨테이너 다시 표시
                searchContainer.visibility = View.VISIBLE

                // 2. 하단 내비게이션 바 다시 표시
                bottomNavigationContainer.visibility = View.VISIBLE

                // 3. 홈 탭으로 돌아가기 (다른 탭이 열려있을 수 있으므로)
                selectTab(NavigationTab.HOME)

                // 4. 메인 액션 버튼은 목적지가 있을 때만 표시
                // (updateMainActionButtonVisibility에서 처리됨)
                updateMainActionButtonVisibility()

                // 5. 경로 정보 패널은 숨김 상태로 유지
                // (새로운 경로 요청 시 updateRouteInfo에서 다시 표시됨)
                routeInfoPanel.visibility = View.GONE

/*                // 내비게이션 종료 토스트
                showDebouncedToast(languageManager.getLocalizedString(
                    "내비게이션이 종료되었습니다",
                    "Navigation ended"
                ))*/

                Log.d("MainActivity", "Navigation ended - UI controls restored")
            }
        }
    }

    // 경로 정보 업데이트
    fun updateRouteInfo(distance: String, duration: String, arrivalTime: String) {
        runOnUiThread {
            routeDistanceText.text = distance
            routeDurationText.text = duration
            arrivalTimeText.text = arrivalTime

            // 내비게이션 중이 아닐 때만 상단 경로 정보 패널 표시
            if (!isNavigationActive) {
                routeInfoPanel.visibility = View.VISIBLE
                Log.d("MainActivity", "Route info panel shown (not navigating)")
            } else {
                Log.d("MainActivity", "Route info updated but panel hidden (navigating)")
            }

            Log.d("MainActivity", "Route info updated - Distance: $distance, Duration: $duration, Arrival: $arrivalTime")
        }
    }

    // 경로 정보 숨기기
    fun hideRouteInfo() {
        runOnUiThread {
            routeInfoPanel.visibility = View.GONE
            Log.d("MainActivity", "Route info hidden")
        }
    }

    private fun initializeSearchComponents() {
        try {
            searchManager = SearchManager(this)
            searchUI = SearchUI(
                this,
                searchManager,
                languageManager,
                searchButtonViewModel
            )
            Log.d("MainActivity", "Search components initialized")
        } catch (e: Exception) {
            Log.e("MainActivity", "Search components initialization error", e)
        }
    }

    fun setDestinationFromSearch(point: Point) {
        Log.d("MainActivity", "setDestinationFromSearch called: ${point.longitude()}, ${point.latitude()}")

        if (::navigationManager.isInitialized) {
            // NavigationManager의 setDestination을 사용 (통합된 로직)
            navigationManager.setDestination(point)

            // 직접적인 상태 업데이트는 NavigationManager에서 처리되므로 제거
            // hasDestinationSet = true
            // searchButtonViewModel.setHasDestination(true)

            // 토스트 메시지도 NavigationManager에서 처리되므로 제거하거나 간단히
            runOnUiThread {
                Log.d("MainActivity", "Destination set through search")
            }

            Log.d("MainActivity", "Destination set through NavigationManager from search")
        }
    }

    // 목적지 설정 완료 콜백 개선
    fun onDestinationSet() {
        Log.d("MainActivity", "onDestinationSet called from NavigationManager")

        // 내부 상태 업데이트
        hasDestinationSet = true
        searchButtonViewModel.setHasDestination(true)

        runOnUiThread {
            updateMainActionButtonVisibility()
            Log.d("MainActivity", "UI updated after destination set")
        }
    }

    fun setNavigationActive(active: Boolean) {
        isNavigationActive = active
        searchButtonViewModel.setNavigationActive(active)
    }

    override fun onResume() {
        super.onResume()
        if (::navigationManager.isInitialized) {
            navigationManager.checkNetworkStatus()
        }
    }

    private fun setupMapboxNavigation() {
        val tilePath = File(filesDir, "mbx-offline").absolutePath
        val tileStore = com.mapbox.common.TileStore.create(tilePath)

        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()

        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .navigatorPredictionMillis(3000)
            .build()

        MapboxNavigationApp.setup(navOptions)
        Log.d("MainActivity", "MapboxNavigationApp setup completed with pre-configured TileStore")
    }

    private fun initializeManagers() {
        Log.d("MainActivity", "Initializing basic managers")

        languageManager = LanguageManager(this)
        permissionHelper = PermissionHelper(this, languageManager)
        mapInitializer = MapInitializer(this, languageManager)
        offlineTileManager = OfflineTileManager(
            this,
            mapInitializer.getTileStore(),
            languageManager
        )
        navigationUI = NavigationUI(this, languageManager)

        permissionHelper.setPermissionCallback(object : PermissionHelper.PermissionCallback {
            override fun onPermissionGranted() {
                Log.d("MainActivity", "Location permissions granted")
                initializeAfterPermissionGranted()
            }

            override fun onPermissionDenied() {
                Log.d("MainActivity", "Location permissions denied")
                showPermissionRequiredMessage()
            }
        })

        Log.d("MainActivity", "Basic managers initialized")
    }

    private fun initializeAfterPermissionGranted() {
        try {
            Log.d("MainActivity", "Initializing after permission granted")

            mapInitializer.initializeMap()

            val seoulLocation = Point.fromLngLat(126.978, 37.566)
            mapInitializer.getMapView().mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(seoulLocation)
                    .zoom(15.0)
                    .build()
            )

            Log.d("MainActivity", "Initial camera position set")
            Log.d("MainActivity", "Initialization after permission completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "컴포넌트 초기화 오류", e)
            Toast.makeText(
                this,
                languageManager.getLocalizedString(
                    "내비게이션 초기화 중 오류가 발생했습니다: ${e.message}",
                    "Error during navigation initialization: ${e.message}"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeMapClickListener() {
        Log.d("MainActivity", "Setting up map click listener")

        mapInitializer.setMapClickListener { point ->
            if (::navigationManager.isInitialized && !navigationManager.isNavigating()) {
                Log.d("MainActivity", "=== MAP CLICKED ===")

                // NavigationManager의 setDestination 사용 (통합된 로직)
                navigationManager.setDestination(point)

                // 상태 업데이트는 NavigationManager에서 알림을 통해 처리됨
                // hasDestinationSet = true
                // searchButtonViewModel.setHasDestination(true)

                // UI 업데이트도 NavigationManager의 알림을 통해 처리됨
                // runOnUiThread {
                //     updateMainActionButtonVisibility()
                // }

                return@setMapClickListener true
            }
            false
        }
    }

    private fun showPermissionRequiredMessage() {
        val message = languageManager.getLocalizedString(
            "위치 권한이 필요합니다. 앱을 사용하려면 설정에서 권한을 허용해주세요.",
            "Location permission is required. Please enable it in settings to use the app."
        )
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    fun setSearchButtonEnabled(enabled: Boolean) {
        // 새로운 UI에서는 검색 컨테이너의 활성화/비활성화를 제어
        if (::searchContainer.isInitialized) {
            searchContainer.isEnabled = enabled
            searchContainer.alpha = if (enabled) 1.0f else 0.5f

            // 검색 힌트 텍스트도 업데이트
            val searchHint = findViewById<TextView>(R.id.searchHint)
            if (enabled) {
                searchHint.text = languageManager.getLocalizedString("목적지 검색", "Search destination")
                searchHint.setTextColor(getColor(R.color.hint_text_color))
            } else {
                searchHint.text = languageManager.getLocalizedString("오프라인 모드", "Offline mode")
                searchHint.setTextColor(getColor(R.color.offline_status))
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Log.d("MainActivity", "Configuration changed - orientation: ${newConfig.orientation}")

        // 화면 회전 시 맵뷰 레이아웃 조정 (필요한 경우)
        if (::mapInitializer.isInitialized) {
            // 맵뷰 크기 재조정을 위해 약간의 지연 후 처리
            mapInitializer.getMapView().post {
                mapInitializer.getMapView().requestLayout()
            }
        }

        // 내비게이션 카메라 뷰포트 재계산 (필요한 경우)
        if (::navigationManager.isInitialized && navigationManager.isNavigating()) {
            // 내비게이션 중이면 카메라 뷰포트 다시 계산
            navigationManager.recenterCamera()
        }
    }

    // 토스트 메시지 중복 방지 함수 추가
    private fun showDebouncedToast(message: String) {
        val currentTime = System.currentTimeMillis()

        // Constants에서 적절한 디바운싱 시간 선택
        val debounceTime = when {
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy called")

        // 타이머 정리
        stopExitTimer()

        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }

        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }

        MapboxNavigationApp.disable()
        super.onDestroy()
    }
}