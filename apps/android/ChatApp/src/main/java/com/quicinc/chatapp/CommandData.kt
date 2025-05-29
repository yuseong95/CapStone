package com.quicinc.chatapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

object CommandData {
    // 오직 두 가지 명령어만 처리
    private val commands = setOf("cancel", "start")

    // normalizationMap은 예제대로 사용한다면 남겨두고, 없으면 직접 keyword 매칭 부분만 살립니다.
    private var normalizationMap: Map<String, List<String>> = emptyMap()

    fun initialize(context: Context) {
        try {
            context.assets.open("normalization_map.json").use { inputStream ->
                val reader = InputStreamReader(inputStream, Charsets.UTF_8)
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                normalizationMap = Gson().fromJson(reader, type)
                Log.d("CommandData", "Normalization map loaded: ${normalizationMap.size} entries")
            }
        } catch (e: Exception) {
            Log.e("CommandData", "Failed to load normalization map", e)
        }
    }

    /**
     * 사용자의 텍스트에서 "start" 또는 "cancel" 키워드를 찾아서
     * 일치하면 그 키워드를, 없으면 null을 반환합니다.
     */
    fun getCommandFromText(text: String): String? {
        val lowerText = text.lowercase().trim().replace(" ", "")
        Log.d("whisperDebug", lowerText)

        // 1. normalizationMap에서 variants 검사
        val normalized = normalizationMap.entries.firstOrNull { (_, variants) ->
            variants.any { lowerText.contains(it) }
        }?.key

        if (normalized != null && normalized in commands) {
            Log.d("whisperDebug", "normalized command: $normalized")
            return normalized
        }

        // 2. 직접 keyword 매칭
        return commands.firstOrNull { cmd ->
            lowerText.contains(cmd)
        }
    }
}
