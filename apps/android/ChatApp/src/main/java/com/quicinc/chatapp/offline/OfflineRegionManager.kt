package com.quicinc.chatapp.offline
import android.content.Context
import android.util.Log
import com.quicinc.chatapp.R
import com.quicinc.chatapp.ui.LanguageManager
import com.mapbox.common.Cancelable
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

class OfflineRegionManager(
    private val context: Context,
    private val languageManager: LanguageManager,
) {
    private val tileStore: TileStore
    private val offlineManager: OfflineManager
    private var downloadProgressCallback: DownloadProgressCallback? = null
    private var isDownloading = false
    private var currentDownloadRegionName: String? = null
    private var isCancelled = false

    // 현재 다운로드의 Cancelable 객체 저장
    private var currentDownloadCancelable: Cancelable? = null

    // SharedPreferences for metadata storage
    private val prefs = context.getSharedPreferences("offline_regions_meta", Context.MODE_PRIVATE)

    // 진행상황 콜백 인터페이스
    interface DownloadProgressCallback {
        fun onDownloadStarted(regionName: String)
        fun onDownloadProgress(progress: Float)
        fun onDownloadCompleted(regionName: String)
        fun onDownloadError(regionName: String, error: String)
    }

    init {
        val tilePath = File(context.filesDir, "mbx-offline").absolutePath
        tileStore = TileStore.create(tilePath)
        offlineManager = OfflineManager()

        Log.d("OfflineRegionManager", "TileStore path: $tilePath")
    }

    fun setDownloadProgressCallback(callback: DownloadProgressCallback) {
        downloadProgressCallback = callback
    }

    // 지역 다운로드 (기존 지역 삭제 후 새 지역 다운로드)
    suspend fun downloadRegion(
        regionName: String,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isDownloading) {
                Log.w("OfflineRegionManager", "Download already in progress")
                return@withContext false
            }

            isDownloading = true
            isCancelled = false
            currentDownloadRegionName = regionName

            Log.d("OfflineRegionManager", "Starting download for region: $regionName")

            // 진행상황 콜백 호출
            downloadProgressCallback?.onDownloadStarted(regionName)

            // 1. 기존 모든 지역 삭제
            if (!deleteAllRegions()) {
                Log.e("OfflineRegionManager", "Failed to delete existing regions")
                resetDownloadState()
                downloadProgressCallback?.onDownloadError(regionName, "Failed to clear existing regions")
                return@withContext false
            }

            // 취소 확인
            if (isCancelled) {
                Log.d("OfflineRegionManager", "Download cancelled during region cleanup")
                resetDownloadState()
                return@withContext false
            }

            // 2. 새 지역 다운로드
            val success = downloadRegionInternal(regionName, minLon, maxLon, minLat, maxLat)

            if (success && !isCancelled) {
                downloadProgressCallback?.onDownloadCompleted(regionName)
            } else if (isCancelled) {
                Log.d("OfflineRegionManager", "Download was cancelled")
                // 취소된 경우 부분적으로 다운로드된 데이터 정리
                cleanupCancelledDownload(regionName)
            } else {
                downloadProgressCallback?.onDownloadError(regionName, "Download failed")
            }

            resetDownloadState()
            return@withContext success && !isCancelled

        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error downloading region: $regionName", e)
            resetDownloadState()
            downloadProgressCallback?.onDownloadError(regionName, e.message ?: "Unknown error")
            return@withContext false
        }
    }

    // 실제 지역 다운로드 수행
    private suspend fun downloadRegionInternal(
        regionName: String,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // 좌표 유효성 검사
            if (!isValidCoordinates(minLon, maxLon, minLat, maxLat)) {
                Log.e("OfflineRegionManager", "Invalid coordinates for region: $regionName")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            // 지역 영역 정의
            val regionBounds = listOf(
                Point.fromLngLat(minLon, minLat),
                Point.fromLngLat(maxLon, minLat),
                Point.fromLngLat(maxLon, maxLat),
                Point.fromLngLat(minLon, maxLat),
                Point.fromLngLat(minLon, minLat)
            )

            val regionGeometry = Polygon.fromLngLats(listOf(regionBounds))

            // 타일셋 디스크립터 생성 (기본 스타일)
            val tilesetDescriptor = offlineManager.createTilesetDescriptor(
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

            // 로드 옵션 설정
            val loadOptions = TileRegionLoadOptions.Builder()
                .geometry(regionGeometry)
                .descriptors(listOf(tilesetDescriptor, navigationTilesetDescriptor))
                .acceptExpired(true)
                .build()

            // 진행상황 콜백
            val progressCallback: (TileRegionLoadProgress) -> Unit = progressCallback@{ progress ->
                // 취소 확인
                if (isCancelled) {
                    Log.d("OfflineRegionManager", "Download progress callback - cancelled")
                    return@progressCallback
                }

                val percentage = if (progress.requiredResourceCount > 0) {
                    progress.completedResourceCount.toFloat() / progress.requiredResourceCount
                } else {
                    0f
                }

                Log.d("OfflineRegionManager", "Download progress: ${(percentage * 100).toInt()}%")
                downloadProgressCallback?.onDownloadProgress(percentage)
            }

            // 다운로드 시작
            Log.d("OfflineRegionManager", "Starting tile region download: $regionName")

            // Cancelable 객체 저장
            val cancelable = tileStore.loadTileRegion(
                regionName,
                loadOptions,
                progressCallback
            ) { expected ->
                // 다운로드 완료 시 Cancelable 정리
                currentDownloadCancelable = null

                if (isCancelled) {
                    Log.d("OfflineRegionManager", "Download completed but was cancelled")
                    continuation.resume(false)
                    return@loadTileRegion
                }

                if (expected.isValue) {
                    val tileRegion = expected.value!!
                    Log.d("OfflineRegionManager", "Download completed successfully: ${tileRegion.id}")

                    // 다운로드 메타데이터 저장
                    saveRegionMetadata(regionName)

                    continuation.resume(true)
                } else {
                    val error = expected.error!!
                    Log.e("OfflineRegionManager", "Download failed: ${error.message}")
                    continuation.resume(false)
                }
            }

            // 현재 다운로드의 Cancelable 저장
            currentDownloadCancelable = cancelable

            // 코루틴 취소 시 실제 Mapbox 다운로드도 취소
            continuation.invokeOnCancellation {
                Log.d("OfflineRegionManager", "Download coroutine cancelled: $regionName")
                isCancelled = true
                // 실제 네이티브 다운로드 취소
                cancelable.cancel()
                currentDownloadCancelable = null
            }

        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error in downloadRegionInternal", e)
            currentDownloadCancelable = null  // 에러 시에도 정리
            continuation.resume(false)
        }
    }

    // 좌표 유효성 검사
    private fun isValidCoordinates(minLon: Double, maxLon: Double, minLat: Double, maxLat: Double): Boolean {
        return minLon >= -180 && maxLon <= 180 && minLat >= -90 && maxLat <= 90 &&
                minLon < maxLon && minLat < maxLat
    }

    // 취소된 다운로드 정리
    private suspend fun cleanupCancelledDownload(regionName: String) {
        Log.d("OfflineRegionManager", "Cleaning up cancelled download: $regionName")

        // 부분적으로 다운로드된 타일 지역 삭제 시도
        try {
            deleteRegion(regionName)
        } catch (e: Exception) {
            Log.w("OfflineRegionManager", "Error cleaning up cancelled download", e)
        }
    }

    // 다운로드 상태 초기화
    private fun resetDownloadState() {
        isDownloading = false
        isCancelled = false
        currentDownloadRegionName = null
        currentDownloadCancelable = null
    }

    //모든 기존 지역 삭제
    private suspend fun deleteAllRegions(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            tileStore.getAllTileRegions { expected ->
                if (expected.isValue) {
                    val regions = expected.value!!
                    if (regions.isEmpty()) {
                        Log.d("OfflineRegionManager", "No existing regions to delete")
                        clearAllRegionMetadata()
                        continuation.resume(true)
                        return@getAllTileRegions
                    }

                    Log.d("OfflineRegionManager", "Deleting ${regions.size} existing regions")
                    var deletedCount = 0
                    val totalCount = regions.size
                    var hasError = false

                    regions.forEach { region ->
                        // 취소 확인
                        if (isCancelled) {
                            Log.d("OfflineRegionManager", "Region deletion cancelled")
                            continuation.resume(false)
                            return@forEach
                        }

                        tileStore.removeTileRegion(region.id) { deleteExpected ->
                            deletedCount++
                            if (deleteExpected.isError) {
                                Log.w("OfflineRegionManager", "Failed to delete region: ${region.id}")
                                hasError = true
                            } else {
                                Log.d("OfflineRegionManager", "Successfully deleted region: ${region.id}")
                            }

                            if (deletedCount == totalCount) {
                                Log.d("OfflineRegionManager", "All existing regions deleted")
                                clearAllRegionMetadata()
                                continuation.resume(!hasError)
                            }
                        }
                    }
                } else {
                    Log.e("OfflineRegionManager", "Failed to get existing regions")
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error deleting all regions", e)
            continuation.resume(false)
        }
    }

    // 다운로드 지역 메타 데이터 저장
    suspend fun getDownloadedRegions(): List<DownloadedRegion> = suspendCancellableCoroutine { continuation ->
        try {
            tileStore.getAllTileRegions { expected ->
                if (expected.isValue) {
                    val regions = expected.value!!
                    val downloadedRegions = regions.map { region ->
                        val metadata = getRegionMetadata(region.id)
                        DownloadedRegion(
                            id = region.id,
                            name = region.id,
                            downloadDate = metadata.downloadDate,
                            size = metadata.estimatedSize
                        )
                    }

                    Log.d("OfflineRegionManager", "Found ${downloadedRegions.size} downloaded regions")
                    continuation.resume(downloadedRegions)
                } else {
                    Log.e("OfflineRegionManager", "Failed to get downloaded regions")
                    continuation.resume(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error getting downloaded regions", e)
            continuation.resume(emptyList())
        }
    }

    // 지역 메타 데이터 저장
    private fun saveRegionMetadata(regionName: String) {
        val currentTime = System.currentTimeMillis()
        val estimatedSize = getEstimatedRegionSizeByName(regionName)

        prefs.edit().apply {
            putLong("${regionName}_download_time", currentTime)
            putLong("${regionName}_estimated_size", estimatedSize)
            apply()
        }

        Log.d("OfflineRegionManager", "Saved metadata for region: $regionName")
    }

    // 지역 메타 데이터 조회
    private fun getRegionMetadata(regionName: String): RegionMetadata {
        val downloadTime = prefs.getLong("${regionName}_download_time", System.currentTimeMillis())
        val estimatedSize = prefs.getLong("${regionName}_estimated_size", getEstimatedRegionSizeByName(regionName))

        return RegionMetadata(
            downloadDate = formatDate(Date(downloadTime)),
            estimatedSize = formatSize(estimatedSize)
        )
    }

    // 지역 메타 데이터 삭제
    private fun removeRegionMetadata(regionName: String) {
        prefs.edit().apply {
            remove("${regionName}_download_time")
            remove("${regionName}_estimated_size")
            apply()
        }

        Log.d("OfflineRegionManager", "Removed metadata for region: $regionName")
    }

    // 모든 지역 메타데이터 삭제
    private fun clearAllRegionMetadata() {
        prefs.edit().clear().apply()
        Log.d("OfflineRegionManager", "Cleared all region metadata")
    }

    // 특정 지역 삭제
    suspend fun deleteRegion(regionId: String): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            Log.d("OfflineRegionManager", "Deleting region: $regionId")

            tileStore.removeTileRegion(regionId) { expected ->
                if (expected.isValue) {
                    Log.d("OfflineRegionManager", "Successfully deleted region: $regionId")

                    // 메타데이터도 함께 삭제
                    removeRegionMetadata(regionId)

                    continuation.resume(true)
                } else {
                    val error = expected.error!!
                    Log.e("OfflineRegionManager", "Failed to delete region: $regionId, error: ${error.message}")
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineRegionManager", "Error deleting region: $regionId", e)
            continuation.resume(false)
        }
    }

    // 현재 다운로드 취소
    fun cancelCurrentDownload() {
        if (isDownloading && currentDownloadRegionName != null) {
            Log.d("OfflineRegionManager", "Cancelling download: $currentDownloadRegionName")

            // 실제 Mapbox 다운로드 즉시 취소
            currentDownloadCancelable?.let { cancelable ->
                Log.d("OfflineRegionManager", "Calling native cancel()")
                cancelable.cancel()
            }
            currentDownloadCancelable = null

            // 플래그 설정 (기존 로직 유지)
            isCancelled = true

            // 콜백으로 취소 알림
            currentDownloadRegionName?.let { regionName ->
                downloadProgressCallback?.onDownloadError(
                    regionName,
                    languageManager.getLocalizedString("다운로드가 취소되었습니다", "Download cancelled")
                )
            }
        } else {
            Log.w("OfflineRegionManager", "No active download to cancel")
        }
    }

    // 다운로드 상태 확인
    fun isDownloading(): Boolean = isDownloading

    // 현재 다운로드 중인 지역명 반환
    fun getCurrentDownloadRegion(): String? = currentDownloadRegionName

    // 취소 상태 확인
    fun isCancelled(): Boolean = isCancelled

    // 날짜 포캣팅
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(date)
    }

    // 파일 크기 포맷팅
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // 지역 크기 추정
    private fun getEstimatedRegionSizeByName(regionName: String): Long {
        // 지역별 추정 크기 (MB 단위를 바이트로 변환)
        val estimatedSizeMB = when {
            // 수도권 (서울+경기+인천) - 대용량
            regionName.contains("수도권", ignoreCase = true) ||
                    regionName.contains("metropolitan", ignoreCase = true) -> 400L

            // 광역시들
            regionName.contains("seoul", ignoreCase = true) || regionName.contains("서울", ignoreCase = true) -> 150L
            regionName.contains("busan", ignoreCase = true) || regionName.contains("부산", ignoreCase = true) -> 120L
            regionName.contains("daegu", ignoreCase = true) || regionName.contains("대구", ignoreCase = true) -> 110L
            regionName.contains("incheon", ignoreCase = true) || regionName.contains("인천", ignoreCase = true) -> 130L
            regionName.contains("gwangju", ignoreCase = true) || regionName.contains("광주", ignoreCase = true) -> 100L
            regionName.contains("daejeon", ignoreCase = true) || regionName.contains("대전", ignoreCase = true) -> 105L
            regionName.contains("ulsan", ignoreCase = true) || regionName.contains("울산", ignoreCase = true) -> 95L

            // 도 지역들
            regionName.contains("gyeonggi", ignoreCase = true) || regionName.contains("경기", ignoreCase = true) -> 350L
            regionName.contains("gangwon", ignoreCase = true) || regionName.contains("강원", ignoreCase = true) -> 280L
            regionName.contains("chungbuk", ignoreCase = true) || regionName.contains("충청북", ignoreCase = true) -> 200L
            regionName.contains("chungnam", ignoreCase = true) || regionName.contains("충청남", ignoreCase = true) -> 220L
            regionName.contains("jeonbuk", ignoreCase = true) || regionName.contains("전라북", ignoreCase = true) -> 200L
            regionName.contains("jeonnam", ignoreCase = true) || regionName.contains("전라남", ignoreCase = true) -> 250L
            regionName.contains("gyeongbuk", ignoreCase = true) || regionName.contains("경상북", ignoreCase = true) -> 280L
            regionName.contains("gyeongnam", ignoreCase = true) || regionName.contains("경상남", ignoreCase = true) -> 240L
            regionName.contains("jeju", ignoreCase = true) || regionName.contains("제주", ignoreCase = true) -> 80L

            // === 일본 도시들 추가 ===
            regionName.contains("tokyo", ignoreCase = true) || regionName.contains("도쿄", ignoreCase = true) -> 350L
            regionName.contains("osaka", ignoreCase = true) || regionName.contains("오사카", ignoreCase = true) -> 280L
            regionName.contains("kyoto", ignoreCase = true) || regionName.contains("교토", ignoreCase = true) -> 200L
            regionName.contains("nagoya", ignoreCase = true) || regionName.contains("나고야", ignoreCase = true) -> 250L
            regionName.contains("yokohama", ignoreCase = true) || regionName.contains("요코하마", ignoreCase = true) -> 220L
            regionName.contains("kobe", ignoreCase = true) || regionName.contains("고베", ignoreCase = true) -> 180L

            // === 미국 도시들 추가 ===
            regionName.contains("new york", ignoreCase = true) || regionName.contains("뉴욕", ignoreCase = true) -> 450L
            regionName.contains("los angeles", ignoreCase = true) || regionName.contains("로스앤젤레스", ignoreCase = true) -> 400L
            regionName.contains("chicago", ignoreCase = true) || regionName.contains("시카고", ignoreCase = true) -> 350L
            regionName.contains("san francisco", ignoreCase = true) || regionName.contains("샌프란시스코", ignoreCase = true) -> 250L
            regionName.contains("las vegas", ignoreCase = true) || regionName.contains("라스베이거스", ignoreCase = true) -> 200L
            regionName.contains("miami", ignoreCase = true) || regionName.contains("마이애미", ignoreCase = true) -> 180L
            regionName.contains("seattle", ignoreCase = true) || regionName.contains("시애틀", ignoreCase = true) -> 220L

            // 기타
            regionName.length > 20 -> 300L // 긴 이름 (주로 해외 지역)
            regionName.length > 10 -> 200L // 중간 길이
            else -> 150L // 기본값
        }
        return estimatedSizeMB * 1024 * 1024 // MB를 바이트로 변환
    }

    // 리소스 정리
    fun cleanup() {
        Log.d("OfflineRegionManager", "Cleaning up OfflineRegionManager")

        // 진행 중인 다운로드가 있으면 취소
        if (isDownloading) {
            cancelCurrentDownload()
        }

        downloadProgressCallback = null
        resetDownloadState()
    }
}

// 지역 메타데이터를 담는 데이터 클래스

data class RegionMetadata(
    val downloadDate: String,
    val estimatedSize: String
)