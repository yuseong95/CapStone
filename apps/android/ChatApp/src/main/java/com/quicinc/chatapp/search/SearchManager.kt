package com.quicinc.chatapp.search;
import android.content.Context
import android.util.Log
import com.mapbox.common.MapboxOptions
import com.mapbox.search.ApiType
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings

class SearchManager(
    private val context: Context,
) {
    // Search Box API - POI/랜드마크 검색용 SearchEngine
    internal val searchBoxEngine: SearchEngine by lazy {
        // 액세스 토큰 설정
        if (MapboxOptions.accessToken.isEmpty()) {
            MapboxOptions.accessToken = context.getString(com.quicinc.chatapp.R.string.mapbox_access_token)
        }

        SearchEngine.createSearchEngineWithBuiltInDataProviders(
            ApiType.SEARCH_BOX, // POI/랜드마크 검색
            SearchEngineSettings()
        )
    }

    // Geocoding API - 주소 검색용 SearchEngine
    internal val geocodingEngine: SearchEngine by lazy {
        // 액세스 토큰 설정
        if (MapboxOptions.accessToken.isEmpty()) {
            MapboxOptions.accessToken = context.getString(com.quicinc.chatapp.R.string.mapbox_access_token)
        }

        SearchEngine.createSearchEngineWithBuiltInDataProviders(
            ApiType.GEOCODING, // 주소 검색
            SearchEngineSettings()
        )
    }

    // 검색 결과 타입 구분을 위한 enum
    enum class SearchResultType {
        POI,        // Search Box API 결과 (POI/랜드마크)
        ADDRESS     // Geocoding API 결과 (주소)
    }

    // 검색 결과를 담는 래퍼 클래스
    data class CombinedSearchResult(
        val result: com.mapbox.search.result.SearchResult,
        val type: SearchResultType,
        val source: String // "Search Box" 또는 "Geocoding"
    )

    // 이중 검색 콜백 인터페이스
    interface DualSearchCallback {
        fun onSearchBoxResults(results: List<com.mapbox.search.result.SearchResult>)
        fun onGeocodingResults(results: List<com.mapbox.search.result.SearchResult>)
        fun onCombinedResults(results: List<CombinedSearchResult>)
        fun onError(source: String, error: Exception)
        fun onSearchCompleted() // 모든 검색이 완료되었을 때
    }

    // 리소스 정리
    fun cleanup() {
        Log.d("SearchManager", "SearchManager cleanup completed")
    }
}