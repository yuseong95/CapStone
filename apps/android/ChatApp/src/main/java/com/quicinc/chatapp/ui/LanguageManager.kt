package com.quicinc.chatapp.ui;
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.quicinc.chatapp.utils.Constants

class LanguageManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // 현재 선택된 언어
    var currentLanguage: String = Constants.LANG_KOREAN
        private set

    // 현재 선택된 내비게이션 모드 추가
    var currentNavigationMode: String = Constants.DEFAULT_NAVIGATION_MODE
        private set

    init {
        // 저장된 언어 설정 로드
        loadSavedLanguage()
        // 저장된 내비게이션 모드 로드
        loadSavedNavigationMode()
    }

    private fun loadSavedLanguage() {
        currentLanguage = prefs.getString(Constants.PREF_LANG_KEY, Constants.LANG_KOREAN)
            ?: Constants.LANG_KOREAN
    }

    private fun loadSavedNavigationMode() {
        currentNavigationMode = prefs.getString(Constants.PREF_NAVIGATION_MODE_KEY, Constants.DEFAULT_NAVIGATION_MODE)
            ?: Constants.DEFAULT_NAVIGATION_MODE
    }

    fun changeLanguage(newLanguage: String): Boolean {
        if (currentLanguage == newLanguage) return false

        currentLanguage = newLanguage

        prefs.edit {
            putString(Constants.PREF_LANG_KEY, currentLanguage)
        }

        return true
    }

    // 내비게이션 모드 변경 기능 추가
    fun changeNavigationMode(newMode: String): Boolean {
        if (currentNavigationMode == newMode) return false

        // 유효한 모드인지 확인
        if (!isValidNavigationMode(newMode)) return false

        currentNavigationMode = newMode

        prefs.edit {
            putString(Constants.PREF_NAVIGATION_MODE_KEY, currentNavigationMode)
        }

        return true
    }

    private fun isValidNavigationMode(mode: String): Boolean {
        return mode in listOf(
            Constants.NAVIGATION_MODE_DRIVING,
            Constants.NAVIGATION_MODE_WALKING,
            Constants.NAVIGATION_MODE_CYCLING
        )
    }

    // 현재 언어에 맞는 문자열 반환 유틸리티 메소드
    fun getLocalizedString(koreanText: String, englishText: String): String {
        return if (currentLanguage == Constants.LANG_KOREAN) koreanText else englishText
    }

    // 내비게이션 모드에 따른 다국어 문자열 반환
    fun getNavigationModeDisplayName(mode: String): String {
        return when (mode) {
            Constants.NAVIGATION_MODE_DRIVING -> getLocalizedString("자동차", "Driving")
            Constants.NAVIGATION_MODE_WALKING -> getLocalizedString("도보", "Walking")
            Constants.NAVIGATION_MODE_CYCLING -> getLocalizedString("자전거", "Cycling")
            else -> getLocalizedString("자동차", "Driving")
        }
    }

    // 현재 내비게이션 모드의 표시 이름 반환
    fun getCurrentNavigationModeDisplayName(): String {
        return getNavigationModeDisplayName(currentNavigationMode)
    }
}