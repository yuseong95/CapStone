package com.quicinc.chatapp.map
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.ui.LanguageManager
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions

class OfflineTileManager(
    private val context: Context,
    private val tileStore: TileStore,
    private val languageManager: LanguageManager
) {
    // 수도권 통합 지역명 (오프라인 다운로드 페이지와 일치)
    private val INITIAL_REGION_NAME = "수도권 (서울·경기·인천)"

    init {
        // 초기 타일 리전 존재 여부 확인 (첫 실행 시에만)
        checkInitialTileRegionExists()
    }

    private fun checkInitialTileRegionExists() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!

                // 어떤 지역이라도 다운로드되어 있으면 초기 다운로드 스킵
                if (regions.isEmpty()) {
                    Log.d("OfflineTiles", "No existing regions found, downloading initial metropolitan area")
                    downloadInitialOfflineTiles()
                } else {
                    Log.d("OfflineTiles", "Found existing regions: ${regions.map { it.id }}")

                    // 기존 지역이 있지만 수도권 통합 지역이 아닌 경우 알림
                    val hasMetropolitanRegion = regions.any {
                        it.id.contains("수도권") || it.id.contains("metropolitan", true)
                    }

                    if (!hasMetropolitanRegion) {
                        Log.d("OfflineTiles", "Legacy regions found, user can update through settings")
                        showLegacyRegionNotification()
                    }
                }
            } else {
                // 리전 확인 중 오류 발생, 초기 다운로드 시도
                Log.w("OfflineTiles", "Error checking existing regions, attempting initial download")
                downloadInitialOfflineTiles()
            }
        }
    }

    private fun showLegacyRegionNotification() {
        (context as? Navi)?.runOnUiThread {
            val message = languageManager.getLocalizedString(
                "기존 지도 데이터가 발견되었습니다. 설정에서 최신 수도권 통합 지역으로 업데이트할 수 있습니다.",
                "Legacy map data found. You can update to the new metropolitan area in settings."
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun downloadInitialOfflineTiles() {
        // 수도권 (서울+경기+인천) 통합 지역 다운로드
        // 오프라인 다운로드 페이지의 좌표와 정확히 일치시킴
        val metropolitanAreaBounds = listOf(
            Point.fromLngLat(126.4, 37.2),  // 서쪽 하단
            Point.fromLngLat(127.6, 37.2),  // 동쪽 하단
            Point.fromLngLat(127.6, 38.0),  // 동쪽 상단
            Point.fromLngLat(126.4, 38.0),  // 서쪽 상단
            Point.fromLngLat(126.4, 37.2)   // 시작점으로 닫기
        )

        // 위 좌표로 Polygon 생성
        val regionGeometry = Polygon.fromLngLats(listOf(metropolitanAreaBounds))

        // OfflineManager 생성
        val offlineManager = OfflineManager()

        // 기본 스타일 타일셋 디스크립터
        val streetTilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.MAPBOX_STREETS)
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // 내비게이션용 타일셋 디스크립터
        val navigationTilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/navigation-day-v1")
                .minZoom(6)
                .maxZoom(16)
                .build()
        )

        // TileRegionLoadOptions 생성
        val tileRegionLoadOptions = TileRegionLoadOptions.Builder()
            .geometry(regionGeometry)
            .acceptExpired(true)
            .descriptors(listOf(streetTilesetDescriptor, navigationTilesetDescriptor))
            .build()

        // 진행 상황 모니터링 콜백
        val progressCallback: (TileRegionLoadProgress) -> Unit = { progress ->
            val percentage = if (progress.requiredResourceCount > 0) {
                (progress.completedResourceCount.toFloat() / progress.requiredResourceCount) * 100
            } else {
                0f
            }

            Log.d("OfflineTiles", "Initial metropolitan area download progress: ${percentage.toInt()}%")

            // 10%마다 로그 출력으로 사용자에게 진행상황 알림
            if (percentage.toInt() % 10 == 0 && percentage > 0) {
                (context as? Navi)?.runOnUiThread {
                    val message = languageManager.getLocalizedString(
                        "수도권 지역 다운로드 중... ${percentage.toInt()}%",
                        "Downloading metropolitan area... ${percentage.toInt()}%"
                    )
                    Log.d("OfflineTiles", message)
                }
            }
        }

        Log.d("OfflineTiles", "Starting initial metropolitan area download: $INITIAL_REGION_NAME")

        // 타일 리전 다운로드
        tileStore.loadTileRegion(
            INITIAL_REGION_NAME,
            tileRegionLoadOptions,
            progressCallback
        ) { expected ->
            if (expected.isValue) {
                // 다운로드 성공
                val tileRegion = expected.value!!
                Log.d("OfflineTiles", "Initial metropolitan area download completed successfully! Region: ${tileRegion.id}")

                // 성공 메시지 표시
                (context as? Navi)?.runOnUiThread {
                    val message = languageManager.getLocalizedString(
                        "수도권 통합 지역 오프라인 지도 다운로드 완료\n(서울특별시, 경기도, 인천광역시 포함)",
                        "Metropolitan area offline map download completed\n(Seoul, Gyeonggi, Incheon included)"
                    )

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }

                // 메타데이터 저장
                saveInitialRegionMetadata()

            } else {
                // 다운로드 실패
                val error = expected.error!!
                Log.e("OfflineTiles", "Initial metropolitan area download failed: ${error.message}")

                (context as? Navi)?.runOnUiThread {
                    val message = languageManager.getLocalizedString(
                        "초기 수도권 지역 다운로드 실패: ${error.message}\n\n" +
                                "설정 > 오프라인 지도에서 수동으로 다운로드할 수 있습니다.",
                        "Initial metropolitan area download failed: ${error.message}\n\n" +
                                "You can manually download from Settings > Offline Maps."
                    )

                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 초기 지역 메타데이터 저장
    private fun saveInitialRegionMetadata() {
        try {
            val prefs = context.getSharedPreferences("offline_regions_meta", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()
            val estimatedSize = 400L * 1024 * 1024 // 400MB (수도권 크기)

            prefs.edit().apply {
                putLong("${INITIAL_REGION_NAME}_download_time", currentTime)
                putLong("${INITIAL_REGION_NAME}_estimated_size", estimatedSize)
                apply()
            }

            Log.d("OfflineTiles", "Initial region metadata saved for: $INITIAL_REGION_NAME")
        } catch (e: Exception) {
            Log.e("OfflineTiles", "Error saving initial region metadata", e)
        }
    }

    // 수도권 지역 업데이트 (기존 사용자용)
    fun updateToMetropolitanArea() {
        Log.d("OfflineTiles", "Updating existing regions to metropolitan area")

        // 기존 지역들 삭제 후 수도권 통합 지역 다운로드
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!
                var deletedCount = 0
                val totalCount = regions.size

                if (totalCount == 0) {
                    // 기존 지역이 없으면 바로 다운로드
                    downloadInitialOfflineTiles()
                    return@getAllTileRegions
                }

                // 기존 지역들 삭제
                regions.forEach { region ->
                    tileStore.removeTileRegion(region.id) { deleteExpected ->
                        deletedCount++
                        if (deleteExpected.isError) {
                            Log.w("OfflineTiles", "Failed to delete legacy region: ${region.id}")
                        } else {
                            Log.d("OfflineTiles", "Successfully deleted legacy region: ${region.id}")
                        }

                        if (deletedCount == totalCount) {
                            Log.d("OfflineTiles", "All legacy regions deleted, starting metropolitan area download")
                            downloadInitialOfflineTiles()
                        }
                    }
                }
            } else {
                Log.e("OfflineTiles", "Failed to get existing regions for update")
            }
        }
    }

    // 현재 다운로드된 지역이 수도권 통합 지역인지 확인
    fun hasMetropolitanArea(callback: (Boolean) -> Unit) {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regions = expected.value!!
                val hasMetropolitan = regions.any {
                    it.id.contains("수도권") || it.id.contains("metropolitan", true) ||
                            it.id == INITIAL_REGION_NAME
                }
                callback(hasMetropolitan)
            } else {
                callback(false)
            }
        }
    }

    // 리소스 정리
    fun cleanup() {
        Log.d("OfflineTiles", "OfflineTileManager cleanup completed")
    }
}