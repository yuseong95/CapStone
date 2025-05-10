package com.capstone.ttm

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.capstone.ttm.R
import com.capstone.ttm.databinding.ActivityOfflineBinding
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Value
import com.mapbox.common.*
import com.mapbox.common.BuildConfig
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.*
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import kotlinx.coroutines.launch
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions


class MainActivity : AppCompatActivity(), PermissionsListener {
    // We use the default tile store
    private lateinit var mapboxNavigation: MapboxNavigation
    private val tileStore: TileStore = MapboxOptions.mapsOptions.tileStore!!
    private val offlineManager: OfflineManager = OfflineManager()
    private val offlineLogsAdapter: OfflineLogsAdapter = OfflineLogsAdapter()
    private lateinit var binding: ActivityOfflineBinding
    // ── 클래스 맨 위에
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private var mapStyle: Style? = null
    lateinit var permissionsManager: PermissionsManager

    private val cancelables = mutableListOf<Cancelable>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // 위치 권한이 이미 허용됨: 오프라인 네비게이션 로직 실행
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
        binding = ActivityOfflineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MapboxOptions.accessToken = "sk.eyJ1IjoiaHVuOTI2NCIsImEiOiJjbTk3NnZkeTMwNGlqMmlwbGZ1a2F0MDY2In0.GjwPINRgqdA1OfEW0LjbGw"   // ← 토큰 하드코딩 대신 BuildConfig 권장

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = offlineLogsAdapter

        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()
        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .build()

        mapboxNavigation = MapboxNavigationProvider.create(navOptions)

        prepareDownloadButton()
    }

    private fun prepareDownloadButton() {
        updateButton("DOWNLOAD") { downloadOfflineRegion() }
    }

    private fun prepareCancelButton() {
        updateButton("CANCEL DOWNLOAD") {
            cancelables.forEach { it.cancel() }
            cancelables.clear()
            prepareDownloadButton()
        }
    }

    private fun prepareViewMapButton() {
        OfflineSwitch.getInstance().isMapboxStackConnected = false
        logInfoMessage("Mapbox network stack disabled.")

        updateButton("VIEW SATELLITE STREET MAP") {
            val context = this@MainActivity
            val mapView = MapView(context, MapInitOptions(context, styleUri = Style.SATELLITE_STREETS))
            binding.container.addView(mapView)

            // ★ 스타일 로드 후 RouteLine API/뷰 초기화
            mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS) { style ->
                mapStyle = style
                routeLineApi = MapboxRouteLineApi(
                    MapboxRouteLineApiOptions.Builder().build()
                )
                routeLineView = MapboxRouteLineView(
                    MapboxRouteLineViewOptions.Builder(this).build()
                )
            }

            mapView.mapboxMap.setCamera(CameraOptions.Builder().zoom(ZOOM).center(HANSUNG_UNIV).build())
            mapView.annotations.createCircleAnnotationManager().create(
                CircleAnnotationOptions().withPoint(HANSUNG_UNIV).withCircleColor(Color.RED)
            )

            prepareViewStandardMapButton(mapView)
        }
    }
    private fun prepareViewStandardMapButton(mapView: MapView) {
        lifecycleScope.launch {
            updateButton("VIEW STANDARD MAP") {
                // Load standard style and animate camera to show 3D buildings.
                mapView.mapboxMap.loadStyle(Style.STANDARD)
                mapView.mapboxMap.flyTo(
                    cameraOptions {
                        center(
                            Point.fromLngLat(127.0060, 37.58817)
                        )
                        zoom(15.0)
                        bearing(356.1)
                        pitch(59.8)
                    },
                    mapAnimationOptions { duration(1000L) }
                )

            }
            startNavigation(mapView)
        }
    }


    private fun updateButton(text: String, listener: View.OnClickListener) {
        binding.button.text = text
        binding.button.setOnClickListener(listener)
    }

    private fun downloadOfflineRegion() {
        // 1. Create style package with loadStylePack() call.

        // A style pack (a Style offline package) contains the loaded style and its resources: loaded
        // sources, fonts, sprites. Style packs are identified with their style URI.

        // Style packs are stored in the disk cache database, but their resources are not subject to
        // the data eviction algorithm and are not considered when calculating the disk cache size.
        cancelables.add(
            offlineManager.loadStylePack(
                Style.SATELLITE_STREETS,
                // Build Style pack load options
                StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .metadata(Value(STYLE_PACK_SATELLITE_STREET_METADATA))
                    .build(),
                { progress ->
                    // Update the download progress to UI
                    updateSatelliteStreetStylePackDownloadProgress(
                        progress.completedResourceCount,
                        progress.requiredResourceCount,
                        "StylePackLoadProgress: $progress"
                    )
                },
                { expected ->
                    expected.value?.let { stylePack ->
                        logSuccessMessage("StylePack downloaded: $stylePack")
                        if (allResourcesDownloadLoaded()) {
                            // ← 반드시 메인 스레드에서 실행
                            runOnUiThread {
                                prepareViewMapButton()
                            }
                        } else {
                            logInfoMessage("Waiting for tile region download to be finished.")
                        }
                    }
                    expected.error?.let { logErrorMessage("StylePackError: $it") }
                }
            )
        )

        // Download standard style pack
        cancelables.add(
            offlineManager.loadStylePack(
                Style.STANDARD,
                // Build Style pack load options
                StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .metadata(Value(STYLE_PACK_STANDARD_METADATA))
                    .build(),
                { progress ->
                    // Update the download progress to UI
                    updateStandardStylePackDownloadProgress(
                        progress.completedResourceCount,
                        progress.requiredResourceCount,
                        "StylePackStandardLoadProgress: $progress"
                    )
                },
                { expected ->
                    expected.value?.let { stylePack ->
                        // Style pack download finishes successfully
                        logSuccessMessage("StylePack downloaded: $stylePack")
                        if (allResourcesDownloadLoaded()) {
                            runOnUiThread { prepareViewMapButton() }
                        } else {
                            logInfoMessage("Waiting for tile region download to be finished.")
                        }
                    }
                    expected.error?.let {
                        // Handle error occurred during the style pack download.
                        logErrorMessage("StylePackError: $it")
                    }
                }
            )
        )

        // 2. Create a tile region with tiles for the satellite street style

        // A Tile Region represents an identifiable geographic tile region with metadata, consisting of
        // a set of tiles packs that cover a given area (a polygon). Tile Regions allow caching tiles
        // packs in an explicit way: By creating a Tile Region, developers can ensure that all tiles in
        // that region will be downloaded and remain cached until explicitly deleted.

        // Creating a Tile Region requires supplying a description of the area geometry, the tilesets
        // and zoom ranges of the tiles within the region.

        // The tileset descriptor encapsulates the tile-specific data, such as which tilesets, zoom ranges,
        // pixel ratio etc. the cached tile packs should have. It is passed to the Tile Store along with
        // the region area geometry to load a new Tile Region.

        // The OfflineManager is responsible for creating tileset descriptors for the given style and zoom range.
        val tilesetDescriptors = listOf(
            offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(Style.SATELLITE_STREETS)
                    .pixelRatio(resources.displayMetrics.density)
                    .minZoom(0)
                    .maxZoom(16)
                    .build()
            ),
            offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(Style.STANDARD)
                    .pixelRatio(resources.displayMetrics.density)
                    .minZoom(0)
                    .maxZoom(16)
                    .build()
            )
        )
        val satDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.SATELLITE_STREETS)
                .pixelRatio(resources.displayMetrics.density)
                .minZoom(0)
                .maxZoom(16)
                .build()
        )
        val stdDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.STANDARD)
                .pixelRatio(resources.displayMetrics.density)
                .minZoom(0)
                .maxZoom(16)
                .build()
        )
        // 네비게이션용 타일셋
        val navDescriptor = mapboxNavigation.tilesetDescriptorFactory.getLatest()

        // 3) 영역을 충분히 넓게 잡은 폴리곤 (한성대입구역 주변 + 캠퍼스)
        val regionPolygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(127.000, 37.595),  // 서북
            Point.fromLngLat(127.000, 37.575),  // 남서
            Point.fromLngLat(127.015, 37.575),  // 남동
            Point.fromLngLat(127.015, 37.595),  // 동북
            Point.fromLngLat(127.000, 37.595)   // 닫힌 고리
        )))


        // Use the the default TileStore to load this region. You can create custom TileStores that are
        // unique for a particular file path, i.e. there is only ever one TileStore per unique path.

        // Note that the TileStore path must be the same with the TileStore used when initialise the MapView.
        // 4) 한번에 스타일·맵·네비 타일 다운로드
        val descriptors = listOf(satDescriptor, stdDescriptor, navDescriptor)
        cancelables.add(
            tileStore.loadTileRegion(
                TILE_REGION_ID,
                TileRegionLoadOptions.Builder()
                    .geometry(regionPolygon)
                    .descriptors(descriptors)
                    .acceptExpired(true)
                    .networkRestriction(NetworkRestriction.NONE)
                    .build(),
                { progress ->
                    updateTileRegionDownloadProgress(
                        progress.completedResourceCount,
                        progress.requiredResourceCount,
                        "TileRegionLoadProgress: $progress"
                    )
                }
            ) { expected ->
                expected.value?.let { region ->
                    logSuccessMessage("TileRegion downloaded: $region")
                    if (allResourcesDownloadLoaded()) {
                        // **오프라인 모드 전환은 여기서만!**
                        runOnUiThread {
                            OfflineSwitch.getInstance().isMapboxStackConnected = false
                            logInfoMessage("Mapbox network stack disabled.")
                            prepareViewMapButton()
                        }
                    }
                }
                expected.error?.let { logErrorMessage("TileRegionError: $it") }
            }
        )
        prepareCancelButton()
    }

    private fun allResourcesDownloadLoaded(): Boolean = binding.satelliteStreetsStylePackDownloadProgress.max > 0 &&
            binding.standardStylePackDownloadProgress.max > 0 &&
            binding.tilePackDownloadProgress.max > 0 &&
            binding.satelliteStreetsStylePackDownloadProgress.progress == binding.satelliteStreetsStylePackDownloadProgress.max &&
            binding.standardStylePackDownloadProgress.progress == binding.standardStylePackDownloadProgress.max &&
            binding.tilePackDownloadProgress.progress == binding.tilePackDownloadProgress.max



    private fun removeOfflineRegions() {
        // Remove the tile region with the tile region ID.
        // Note this will not remove the downloaded tile packs, instead, it will just mark the tileset
        // not a part of a tile region. The tiles still exists as a predictive cache in TileStore.
        tileStore.removeTileRegion(TILE_REGION_ID)

        // Remove the style pack with the style url.
        // Note this will not remove the downloaded style pack, instead, it will just mark the resources
        // not a part of the existing style pack. The resources still exists as disk cache.
        offlineManager.removeStylePack(Style.SATELLITE_STREETS)
        offlineManager.removeStylePack(Style.STANDARD)

        MapboxMap.clearData {
            it.error?.let { error ->
                logErrorMessage(error)
            }
        }

        // Explicitly clear ambient cache data (so that if we try to download tile store regions again - it would actually truly download it from network).
        // Ambient cache data is anything not associated with an offline region or a style pack, including predictively cached data.
        // Note that it is advisable to rely on internal TileStore implementation to clear cache when needed.
        tileStore.clearAmbientCache {
            it.error?.let { error ->
                logErrorMessage(error.message)
            }
        }

        // Reset progressbar.
        updateSatelliteStreetStylePackDownloadProgress(0, 0)
        updateStandardStylePackDownloadProgress(0, 0)
        updateTileRegionDownloadProgress(0, 0)
    }

    private fun updateSatelliteStreetStylePackDownloadProgress(progress: Long, max: Long, message: String? = null) {
        binding.satelliteStreetsStylePackDownloadProgress.max = max.toInt()
        binding.satelliteStreetsStylePackDownloadProgress.progress = progress.toInt()
        message?.let {
            offlineLogsAdapter.addLog(OfflineLog.StylePackProgress(it))
        }
    }

    private fun updateStandardStylePackDownloadProgress(progress: Long, max: Long, message: String? = null) {
        binding.standardStylePackDownloadProgress.max = max.toInt()
        binding.standardStylePackDownloadProgress.progress = progress.toInt()
        message?.let {
            offlineLogsAdapter.addLog(OfflineLog.StylePackProgress(it))
        }
    }

    private fun updateTileRegionDownloadProgress(progress: Long, max: Long, message: String? = null) {
        binding.tilePackDownloadProgress.max = max.toInt()
        binding.tilePackDownloadProgress.progress = progress.toInt()
        message?.let {
            offlineLogsAdapter.addLog(OfflineLog.TilePackProgress(it))
        }
    }

    private fun logInfoMessage(message: String) {
        offlineLogsAdapter.addLog(OfflineLog.Info(message))
    }

    private fun logErrorMessage(message: String) {
        offlineLogsAdapter.addLog(OfflineLog.Error(message))
    }

    private fun logSuccessMessage(message: String) {
        offlineLogsAdapter.addLog(OfflineLog.Success(message))
    }
    private fun startNavigation(mapView: MapView) {
        val routingTilesOptions = RoutingTilesOptions.Builder()
            .tileStore(tileStore)
            .build()
        val navOptions = NavigationOptions.Builder(this)
            .routingTilesOptions(routingTilesOptions)
            .build()
        mapboxNavigation = MapboxNavigationProvider.create(navOptions)

        val origin = Point.fromLngLat(127.00611, 37.58833)
        val destination = HANSUNG_UNIV   // 예: 한성대 정문

        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(origin, destination))
            .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: String
                ) {
                    if (routes.isNotEmpty()) {
                        mapboxNavigation.setNavigationRoutes(routes)
                        mapboxNavigation.startTripSession()

                        /* ★ 루트 라인 그리기 */
                        routeLineApi.setNavigationRoutes(routes) { drawData ->
                            mapStyle?.let { routeLineView.renderRouteDrawData(it, drawData) }
                        }

                        /* ★ 진행 상황에 따라 밴리싱 라인 업데이트 */
                        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                    }
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e(TAG, "Route request failed: $reasons")
                }

                override fun onCanceled(routeOptions: RouteOptions, @RouterOrigin routerOrigin: String) {
                    Log.d(TAG, "Route request canceled")
                }
            }
        )
    }

    /* ★ RouteProgressObserver 구현 */
    private val routeProgressObserver = RouteProgressObserver { progress ->
        mapStyle?.let { style ->
            routeLineApi.updateWithRouteProgress(progress) { update ->
                routeLineView.renderRouteLineUpdate(style, update)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cancelables.forEach { it.cancel() }
        cancelables.clear()

        /* ★ RouteLine/Observer 해제 */
        if (::mapboxNavigation.isInitialized) {
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)

        }
        if (::routeLineApi.isInitialized) {
            routeLineApi.cancel()
            routeLineView.cancel()
        }

        removeOfflineRegions()
        OfflineSwitch.getInstance().isMapboxStackConnected = true
    }

    private class OfflineLogsAdapter : RecyclerView.Adapter<OfflineLogsAdapter.ViewHolder>() {
        private var isUpdating: Boolean = false
        private val updateHandler = Handler(Looper.getMainLooper())
        private val logs = ArrayList<OfflineLog>()

        @SuppressLint("NotifyDataSetChanged")
        private val updateRunnable = Runnable {
            notifyDataSetChanged()
            isUpdating = false
        }

        class ViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {
            internal var alertMessageTv: TextView = view.findViewById(R.id.alert_message)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_gesture_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val alert = logs[position]
            holder.alertMessageTv.text = alert.message
            holder.alertMessageTv.setTextColor(
                ContextCompat.getColor(holder.alertMessageTv.context, alert.color)
            )
        }

        override fun getItemCount(): Int {
            return logs.size
        }

        fun addLog(alert: OfflineLog) {
            when (alert) {
                is OfflineLog.Error -> Log.e(TAG, alert.message)
                else -> Log.d(TAG, alert.message)
            }
            logs.add(0, alert)
            if (!isUpdating) {
                isUpdating = true
                updateHandler.postDelayed(updateRunnable, 250)
            }
        }
    }

    private sealed class OfflineLog(val message: String, val color: Int) {
        class Info(message: String) : OfflineLog(message, android.R.color.black)
        class Error(message: String) : OfflineLog(message, android.R.color.holo_red_dark)
        class Success(message: String) : OfflineLog(message, android.R.color.holo_green_dark)
        class TilePackProgress(message: String) : OfflineLog(message, android.R.color.holo_purple)
        class StylePackProgress(message: String) : OfflineLog(message, android.R.color.holo_orange_dark)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val ZOOM = 12.0
        private val HANSUNG_UNIV = Point.fromLngLat(127.009506, 37.582174)
        private const val TILE_REGION_ID = "myTileRegion"
        private const val STYLE_PACK_SATELLITE_STREET_METADATA = "my-satellite-street-style-pack"
        private const val STYLE_PACK_STANDARD_METADATA = "my-standard-style-pack"
        private const val TILE_REGION_METADATA = "my-offline-region"
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        TODO("Not yet implemented")
    }

    override fun onPermissionResult(granted: Boolean) {
        TODO("Not yet implemented")
    }
}
