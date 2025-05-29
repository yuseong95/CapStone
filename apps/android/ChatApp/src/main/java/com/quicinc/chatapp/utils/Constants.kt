package com.quicinc.chatapp.utils;



object Constants {
    // 언어 설정 관련 상수
    const val PREFS_NAME = "NaviSettings"
    const val PREF_LANG_KEY = "language"
    const val LANG_KOREAN = "ko"
    const val LANG_ENGLISH = "en"

    // 경로 요청 관련 상수
    const val ROUTE_REQUEST_DEBOUNCE_TIME = 3000L // 사용자 액션용
    const val AUTO_ROUTE_UPDATE_DEBOUNCE_TIME = 15000L // 자동 업데이트용 (15초)

    // 내비게이션 모드 관련 상수 추가
    const val PREF_NAVIGATION_MODE_KEY = "navigation_mode"

    // 내비게이션 프로필 상수
    const val NAVIGATION_MODE_DRIVING = "driving"
    const val NAVIGATION_MODE_WALKING = "walking"
    const val NAVIGATION_MODE_CYCLING = "cycling"

    // 기본 내비게이션 모드
    const val DEFAULT_NAVIGATION_MODE = NAVIGATION_MODE_DRIVING

    // 토스트 메시지 디바운싱 관련 상수 추가
    const val TOAST_DEBOUNCE_TIME = 3000L // 3초 - 일반 토스트용
    const val OFFLINE_TOAST_DEBOUNCE_TIME = 5000L // 5초 - 오프라인 관련 토스트용
    const val NAVIGATION_TOAST_DEBOUNCE_TIME = 2000L // 2초 - 내비게이션 상태 변경 토스트용

    // 위치 정확도 관련 상수
    const val LOCATION_ACCURACY_THRESHOLD = 0.01 // 10m (위도/경도 차이)
    const val DESTINATION_CHANGE_THRESHOLD = 0.01 // 목적지 변경 감지 임계값
}