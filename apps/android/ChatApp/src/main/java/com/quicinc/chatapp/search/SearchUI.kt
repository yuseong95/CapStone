package com.quicinc.chatapp.search;
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.R
import com.quicinc.chatapp.ui.LanguageManager
import com.mapbox.geojson.Point
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.util.concurrent.atomic.AtomicInteger

class SearchUI(
    private val activity: Navi,
    private val searchManager: SearchManager,
    private val languageManager: LanguageManager,
    private val viewModel: SearchButtonViewModel
) {
    // 새로운 UI 컴포넌트들 (layout에 맞게 수정)
    private val searchOverlay: LinearLayout = activity.findViewById(R.id.searchOverlay)
    private val searchBar: EditText = activity.findViewById(R.id.searchEditText)
    private val searchButton: ImageButton = activity.findViewById(R.id.searchButton)
    private val backButton: ImageButton = activity.findViewById(R.id.backButton)

    // RecyclerView를 사용한 검색 결과 표시
    private val searchResultsRecyclerView: RecyclerView = activity.findViewById(R.id.searchResultsView)
    private lateinit var searchResultsAdapter: CombinedSearchResultsAdapter

    // 검색 상태 관리
    private var searchBoxResults: List<SearchResult> = emptyList()
    private var geocodingResults: List<SearchResult> = emptyList()
    private val pendingSearches = AtomicInteger(0) // 진행 중인 검색 수

    init {
        try {
            setupUI()
            Log.d("SearchUI", "SearchUI initialized successfully")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error initializing SearchUI", e)
        }
    }

    private fun setupUI() {
        // RecyclerView 설정
        setupRecyclerView()

        // UI 컴포넌트 설정
        setupUIComponents()
    }

    private fun setupRecyclerView() {
        try {
            // 람다 파라미터 타입 명시
            searchResultsAdapter = CombinedSearchResultsAdapter { combinedResult: SearchManager.CombinedSearchResult ->
                onSearchResultSelected(combinedResult.result)
            }

            searchResultsRecyclerView.apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = searchResultsAdapter
            }
            Log.d("SearchUI", "RecyclerView setup completed")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error setting up RecyclerView", e)
        }
    }

    private fun setupUIComponents() {
        try {
            // 검색 버튼 클릭 리스너
            searchButton.setOnClickListener {
                val query = searchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    performDualSearch(query)
                } else {
                    showToast(languageManager.getLocalizedString(
                        "검색어를 입력해주세요.",
                        "Please enter a search term."
                    ))
                }
            }

            // 검색창 엔터 리스너
            searchBar.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    val query = searchBar.text.toString().trim()
                    if (query.isNotEmpty()) {
                        performDualSearch(query)
                    }
                    true
                } else {
                    false
                }
            }

            // 뒤로가기 버튼 리스너
            backButton.setOnClickListener {
                Log.d("SearchUI", "Back button clicked")
                viewModel.closeSearchUI()
            }

            Log.d("SearchUI", "UI components setup completed")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error setting up UI components", e)
        }
    }

    fun showSearchUI() {
        Log.d("SearchUI", "Showing search UI")

        try {
            // 네트워크 연결 확인
            if (!isNetworkAvailable()) {
                showToast(languageManager.getLocalizedString(
                    "검색 기능은 온라인 상태에서만 사용 가능합니다.",
                    "Search feature is only available when online."
                ))
                viewModel.closeSearchUI()
                return
            }

            // 검색 힌트 설정
            updateSearchHint()

            // 포커스 및 키보드 표시
            searchBar.requestFocus()
            showKeyboard(searchBar)

            Log.d("SearchUI", "Search UI shown successfully")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error showing search UI", e)
        }
    }

    fun hideSearchUI() {
        Log.d("SearchUI", "Hiding search UI")

        try {
            hideKeyboard()
            searchBar.text.clear()
            searchResultsAdapter.clearResults()

            // 검색 상태 초기화
            searchBoxResults = emptyList()
            geocodingResults = emptyList()
            pendingSearches.set(0)

            Log.d("SearchUI", "Search UI hidden successfully")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error hiding search UI", e)
        }
    }

    private fun updateSearchHint() {
        searchBar.hint = languageManager.getLocalizedString(
            "랜드마크나 주소를 입력하세요 (예: 서울시청, 강남역)",
            "Enter landmark or address (e.g., Seoul City Hall, Gangnam Station)"
        )
    }

    private fun performDualSearch(query: String) {
        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            showToast(languageManager.getLocalizedString(
                "검색 기능은 온라인 상태에서만 사용 가능합니다.",
                "Search feature is only available when online."
            ))
            return
        }

        // 키보드 숨기기
        hideKeyboard()

        Log.d("SearchUI", "Performing dual search for: $query")

        // 검색 상태 초기화
        searchBoxResults = emptyList()
        geocodingResults = emptyList()
        pendingSearches.set(2) // Search Box + Geocoding = 2개

        // 기존 결과 클리어
        searchResultsAdapter.clearResults()

        // 1. Search Box API 검색 (POI/랜드마크)
        performSearchBoxSearch(query)

        // 2. Geocoding API 검색 (주소)
        performGeocodingSearch(query)
    }

    private fun performSearchBoxSearch(query: String) {
        Log.d("SearchUI", "Performing Search Box search for: $query")

        try {
            val searchOptions = SearchOptions.Builder()
                .countries(listOf(IsoCountryCode.SOUTH_KOREA))
                .languages(listOf(
                    if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
                ))
                .limit(10)
                .build()

            searchManager.searchBoxEngine.search(
                query = query,
                options = searchOptions,
                callback = object : SearchSuggestionsCallback {
                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        Log.d("SearchUI", "SearchBox suggestions: ${suggestions.size}")

                        if (suggestions.isNotEmpty()) {
                            processAllSuggestions(suggestions, "SearchBox")
                        } else {
                            onSearchBoxCompleted(emptyList())
                        }
                    }

                    override fun onError(e: Exception) {
                        Log.e("SearchUI", "SearchBox suggestions error: ${e.message}", e)
                        onSearchBoxCompleted(emptyList())
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SearchUI", "Error in performSearchBoxSearch", e)
            onSearchBoxCompleted(emptyList())
        }
    }

    private fun performGeocodingSearch(query: String) {
        Log.d("SearchUI", "Performing Geocoding search for: $query")

        try {
            val searchOptions = SearchOptions.Builder()
                .countries(listOf(IsoCountryCode.SOUTH_KOREA))
                .languages(listOf(
                    if (languageManager.currentLanguage == "ko") IsoLanguageCode.KOREAN else IsoLanguageCode.ENGLISH
                ))
                .limit(10)
                .build()

            searchManager.geocodingEngine.search(
                query = query,
                options = searchOptions,
                callback = object : SearchSuggestionsCallback {
                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        Log.d("SearchUI", "Geocoding suggestions: ${suggestions.size}")

                        if (suggestions.isNotEmpty()) {
                            processAllSuggestions(suggestions, "Geocoding")
                        } else {
                            onGeocodingCompleted(emptyList())
                        }
                    }

                    override fun onError(e: Exception) {
                        Log.e("SearchUI", "Geocoding suggestions error: ${e.message}", e)
                        onGeocodingCompleted(emptyList())
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SearchUI", "Error in performGeocodingSearch", e)
            onGeocodingCompleted(emptyList())
        }
    }

    private fun processAllSuggestions(suggestions: List<SearchSuggestion>, source: String) {
        val allResults = mutableListOf<SearchResult>()
        val remainingSuggestions = AtomicInteger(suggestions.size)

        Log.d("SearchUI", "Processing ${suggestions.size} suggestions from $source")

        suggestions.forEach { suggestion ->
            val engine = if (source == "SearchBox") searchManager.searchBoxEngine else searchManager.geocodingEngine

            engine.select(
                suggestion = suggestion,
                callback = object : SearchSelectionCallback {
                    override fun onResult(
                        suggestion: SearchSuggestion,
                        result: SearchResult,
                        responseInfo: ResponseInfo
                    ) {
                        synchronized(allResults) {
                            allResults.add(result)
                        }
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onResults(
                        suggestion: SearchSuggestion,
                        results: List<SearchResult>,
                        responseInfo: ResponseInfo
                    ) {
                        synchronized(allResults) {
                            allResults.addAll(results)
                        }
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onSuggestions(
                        suggestions: List<SearchSuggestion>,
                        responseInfo: ResponseInfo
                    ) {
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }

                    override fun onError(e: Exception) {
                        Log.e("SearchUI", "$source select error: ${e.message}", e)
                        checkAndCompleteSearch(source, allResults, remainingSuggestions)
                    }
                }
            )
        }
    }

    private fun checkAndCompleteSearch(
        source: String,
        allResults: MutableList<SearchResult>,
        remainingSuggestions: AtomicInteger
    ) {
        val remaining = remainingSuggestions.decrementAndGet()
        if (remaining == 0) {
            Log.d("SearchUI", "$source completed with ${allResults.size} total results")

            when (source) {
                "SearchBox" -> onSearchBoxCompleted(allResults.toList())
                "Geocoding" -> onGeocodingCompleted(allResults.toList())
            }
        }
    }

    private fun onSearchBoxCompleted(results: List<SearchResult>) {
        searchBoxResults = results
        Log.d("SearchUI", "SearchBox search completed with ${results.size} results")

        val remaining = pendingSearches.decrementAndGet()
        updateCombinedResults()

        if (remaining == 0) {
            onAllSearchesCompleted()
        }
    }

    private fun onGeocodingCompleted(results: List<SearchResult>) {
        geocodingResults = results
        Log.d("SearchUI", "Geocoding search completed with ${results.size} results")

        val remaining = pendingSearches.decrementAndGet()
        updateCombinedResults()

        if (remaining == 0) {
            onAllSearchesCompleted()
        }
    }

    private fun updateCombinedResults() {
        activity.runOnUiThread {
            try {
                val combinedResults = mutableListOf<SearchManager.CombinedSearchResult>()

                // Search Box 결과 추가 (POI/랜드마크 우선)
                searchBoxResults.forEach { result ->
                    combinedResults.add(
                        SearchManager.CombinedSearchResult(
                            result = result,
                            type = SearchManager.SearchResultType.POI,
                            source = "Search Box"
                        )
                    )
                }

                // Geocoding 결과 추가 (주소) - 중복 제거
                geocodingResults.forEach { result ->
                    val isDuplicate = combinedResults.any { existing ->
                        isSimilarResult(existing.result, result)
                    }

                    if (!isDuplicate) {
                        combinedResults.add(
                            SearchManager.CombinedSearchResult(
                                result = result,
                                type = SearchManager.SearchResultType.ADDRESS,
                                source = "Geocoding"
                            )
                        )
                    }
                }

                // 결과 정렬: POI 우선, 그 다음 주소
                combinedResults.sortWith(compareBy<SearchManager.CombinedSearchResult> { it.type.ordinal }
                    .thenBy { it.result.name.length })

                Log.d("SearchUI", "Updated combined results: ${combinedResults.size} total")
                searchResultsAdapter.setResults(combinedResults)
            } catch (e: Exception) {
                Log.e("SearchUI", "Error updating combined results", e)
            }
        }
    }

    private fun onAllSearchesCompleted() {
        Log.d("SearchUI", "All searches completed")

        activity.runOnUiThread {
            val totalResults = searchBoxResults.size + geocodingResults.size

            if (totalResults == 0) {
                showToast(languageManager.getLocalizedString(
                    "검색 결과가 없습니다.",
                    "No search results found."
                ))
            } else {
                Log.d("SearchUI", "Search completed - Total: $totalResults results")
            }
        }
    }

    private fun isSimilarResult(result1: SearchResult, result2: SearchResult): Boolean {
        // 이름이 같으면 중복으로 간주
        if (result1.name.equals(result2.name, ignoreCase = true)) {
            return true
        }

        // 좌표가 매우 가까우면 중복으로 간주 (100m 이내)
        val coord1 = result1.coordinate
        val coord2 = result2.coordinate
        val distance = calculateDistance(
            coord1.latitude(), coord1.longitude(),
            coord2.latitude(), coord2.longitude()
        )

        return distance < 0.1 // 100m 이내
    }

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

    private fun onSearchResultSelected(searchResult: SearchResult) {
        Log.d("SearchUI", "Result selected: ${searchResult.name}")

        try {
            val coordinate = searchResult.coordinate
            val resultName = searchResult.name

            val point = Point.fromLngLat(coordinate.longitude(), coordinate.latitude())

            // MainActivity에 목적지 설정
            activity.setDestinationFromSearch(point)

/*            // 목적지 설정 메시지
            showToast(languageManager.getLocalizedString(
                "$resultName 목적지로 설정됨",
                "$resultName set as destination"
            ))*/

            // 검색 UI 숨기기
            viewModel.closeSearchUI()

            Log.d("SearchUI", "Destination set successfully: $resultName")
        } catch (e: Exception) {
            Log.e("SearchUI", "Error setting destination", e)
            showToast(languageManager.getLocalizedString(
                "목적지 설정 중 오류가 발생했습니다.",
                "Error occurred while setting destination."
            ))
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.e("SearchUI", "Error checking network availability", e)
            false
        }
    }

    private fun showKeyboard(view: View) {
        try {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
            Log.e("SearchUI", "Error showing keyboard", e)
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            val view = activity.currentFocus ?: View(activity)
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (e: Exception) {
            Log.e("SearchUI", "Error hiding keyboard", e)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}