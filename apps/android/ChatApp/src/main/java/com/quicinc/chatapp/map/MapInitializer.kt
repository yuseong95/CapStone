package com.quicinc.chatapp.map

import android.content.Context
import android.util.Log
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.R
import com.quicinc.chatapp.ui.LanguageManager
import com.quicinc.chatapp.utils.Constants
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMapsOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import java.io.File

class MapInitializer(private val activity: Navi, private val languageManager: LanguageManager) {
    private val mapView: MapView
    private lateinit var tileStore: TileStore
    lateinit var pointAnnotationManager: PointAnnotationManager
        private set

    companion object {
        // TileStore 설정을 static 메서드로 분리 (MainActivity에서 먼저 호출)
        fun setupGlobalTileStore(context: Context): TileStore {
            val tilePath = File(context.filesDir, "mbx-offline").absolutePath
            val tileStore = TileStore.create(tilePath)

            // 글로벌 TileStore 설정 (MapboxNavigationApp.setup() 전에 필요)
            MapboxMapsOptions.tileStore = tileStore

            Log.d("MapInitializer", "Global TileStore configured at: $tilePath")
            return tileStore
        }
    }

    init {
        // Mapbox 액세스 토큰 설정
        MapboxOptions.accessToken = activity.getString(R.string.mapbox_access_token)

        // TileStore 초기화 (이미 글로벌 설정이 되어 있어야 함)
        initializeTileStore()

        // 맵뷰 참조 가져오기
        mapView = activity.findViewById(R.id.mapView)
    }

    private fun initializeTileStore() {
        val tilePath = File(activity.filesDir, "mbx-offline").absolutePath
        tileStore = TileStore.create(tilePath)

        // 이미 setupGlobalTileStore()에서 설정했지만 안전을 위해 다시 확인
        if (MapboxMapsOptions.tileStore == null) {
            MapboxMapsOptions.tileStore = tileStore
            Log.d("MapInitializer", "TileStore backup configuration applied")
        }
    }

    fun initializeMap() {
        // 맵 스타일 적용
        applyMapStyle(languageManager.currentLanguage)

        // 애노테이션 매니저 초기화
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
    }

    fun setMapClickListener(callback: (Point) -> Boolean) {
        mapView.gestures.addOnMapClickListener(callback)
    }

    fun applyMapStyle(language: String) {
        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            // 한국어 라벨 우선 적용 (글로벌 설정)
            if (language == Constants.LANG_KOREAN) {
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
                    val layer = style.getLayerAs<com.mapbox.maps.extension.style.layers.generated.SymbolLayer>(layerId)
                    layer?.textField("{name_ko}")
                }
            }
        }
    }

    fun getMapView(): MapView = mapView

    fun getTileStore(): TileStore = tileStore
}