package com.quicinc.chatapp.ui;

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout  // 누락된 import 추가
import android.widget.Toast
import com.quicinc.chatapp.Navi
import com.quicinc.chatapp.R
import com.quicinc.chatapp.navigation.NavigationManager
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView

class NavigationUI(
    private val activity: Navi,
    private val languageManager: LanguageManager
) {
    private lateinit var navigationManager: NavigationManager

    // UI 컴포넌트들 - MainActivity와 중복되지 않는 컴포넌트들만 관리
    private val recenterButton: com.google.android.material.floatingactionbutton.FloatingActionButton by lazy {
        activity.findViewById(R.id.recenterButton)
    }
    private val navigationGuidanceContainer: LinearLayout by lazy {
        activity.findViewById(R.id.navigationGuidanceContainer)
    }
    private val maneuverView: MapboxManeuverView by lazy {
        activity.findViewById(R.id.maneuverView)
    }
    private val tripProgressView: MapboxTripProgressView by lazy {
        activity.findViewById(R.id.tripProgressView)
    }

    init {
        Log.d("NavigationUI", "NavigationUI created")
    }

    private fun setupInitialState() {
        try {
            // 초기 상태 설정 - NavigationUI가 직접 관리하는 컴포넌트들만
            recenterButton.visibility = View.GONE
            navigationGuidanceContainer.visibility = View.GONE

            Log.d("NavigationUI", "Initial state setup completed")
        } catch (e: Exception) {
            Log.e("NavigationUI", "Error setting up initial state", e)
        }
    }

    private fun setupButtonListeners() {
        try {
            // 재중앙화 버튼 리스너
            recenterButton.setOnClickListener {
                if (::navigationManager.isInitialized) {
                    navigationManager.recenterCamera()
                } else {
                    Log.w("NavigationUI", "NavigationManager not initialized when recenter button clicked")
                }
            }

            Log.d("NavigationUI", "Button listeners setup completed")
        } catch (e: Exception) {
            Log.e("NavigationUI", "Error setting up button listeners", e)
        }
    }

    fun updateUIForNavigationStart() {
        activity.runOnUiThread {
            try {
                // MainActivity에 UI 상태 변경 요청 (중복 관리 방지)
                activity.setNavigationActive(true)

                // NavigationUI가 직접 관리하는 내비게이션 안내 UI만 표시
                navigationGuidanceContainer.visibility = View.VISIBLE
                maneuverView.visibility = View.VISIBLE
                tripProgressView.visibility = View.VISIBLE

                Log.d("NavigationUI", "Navigation start UI updated - guidance container shown")
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating UI for navigation start", e)
            }
        }
    }

    fun updateUIForNavigationCancel() {
        activity.runOnUiThread {
            try {
                // MainActivity에 UI 상태 변경 요청 (중복 관리 방지)
                activity.setNavigationActive(false)

                // NavigationUI가 직접 관리하는 내비게이션 안내 UI만 숨기기
                navigationGuidanceContainer.visibility = View.GONE
                maneuverView.visibility = View.GONE
                tripProgressView.visibility = View.GONE

                // 재중앙화 버튼 숨기기
                recenterButton.visibility = View.GONE

                Log.d("NavigationUI", "Navigation cancel UI updated - guidance container hidden")
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating UI for navigation cancel", e)
            }
        }
    }

    fun setRecenterButtonVisibility(visible: Boolean) {
        activity.runOnUiThread {
            try {
                recenterButton.visibility = if (visible) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error setting recenter button visibility", e)
            }
        }
    }

    fun updateManeuverView(maneuversResult: Expected<ManeuverError, List<Maneuver>>) {
        activity.runOnUiThread {
            try {
                maneuverView.renderManeuvers(maneuversResult)
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating maneuver view", e)
            }
        }
    }

    fun updateTripProgressView(tripProgress: TripProgressUpdateValue) {
        activity.runOnUiThread {
            try {
                tripProgressView.render(tripProgress)
            } catch (e: Exception) {
                Log.e("NavigationUI", "Error updating trip progress view", e)
            }
        }
    }

    fun showLanguageChangedToast() {
/*        val message = languageManager.getLocalizedString(
            "언어가 한국어로 변경되었습니다.",
            "Language changed to English."
        )
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()*/
    }

    fun setNavigationManager(navManager: NavigationManager) {
        navigationManager = navManager

        // NavigationManager가 설정된 후에 UI 초기화 수행
        setupInitialState()
        setupButtonListeners()

        Log.d("NavigationUI", "NavigationManager set and UI initialized")
    }

    // 언어 변경 시 UI 텍스트 업데이트 (필요시 MainActivity에 요청)
    fun updateLanguage() {
        Log.d("NavigationUI", "Language updated - delegating to MainActivity")
        // MainActivity의 updateUITexts()에서 처리
    }

    @Deprecated("No longer needed - handled by MainActivity")
    fun setNavigationModeContainerVisibility(visible: Boolean) {
        // 새로운 UI에서는 MainActivity에서 처리하므로 더 이상 필요 없음
    }
}