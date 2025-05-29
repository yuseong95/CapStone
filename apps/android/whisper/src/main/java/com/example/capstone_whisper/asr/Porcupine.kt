package com.example.capstone_whisper.asr

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import java.io.File
import java.io.IOException

class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null

    fun start() {
        try {
            val keywordFile = copyAssetToFile("위스퍼_ko_android_v3_0_0.ppn") // 각 AccessKey를 사용해 다운받은 파일
            val modelFile = copyAssetToFile("porcupine_params_ko.pv")

            val keywordPath = keywordFile.absolutePath
            val modelPath = modelFile.absolutePath

            Log.i("PICOVOICE", "[INFO] Porcupine model path: \$modelPath")
            Log.i("PICOVOICE", "[INFO] Porcupine keyword path: \$keywordPath")

            //63n3NaYJi1Y7zv6/Ce3joTnUr5seV4hPdzSkheaLdmMPr6rpjiNmZA==
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey("1uLuIywQggVZNJwNfow679cGRwWKuc4TCH6sPnxJ47w8djNKmnBJBQ==") // AccessKey
                //.setAccessKey("63n3NaYJi1Y7zv6/Ce3joTnUr5seV4hPdzSkheaLdmMPr6rpjiNmZA==")
                .setModelPath(modelPath)
                .setKeywordPaths(arrayOf(keywordPath))
                .setSensitivity(0.7f)
                .build(context) { keywordIndex ->
                    Log.i("Porcupine", "Wake word detected at index: \$keywordIndex")
                    onWakeWordDetected()
                }

            porcupineManager?.start()
        } catch (e: PorcupineException) {
            Log.e("Porcupine", "Failed to initialize Porcupine", e)
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: IOException) {
            Log.e("Porcupine", "Failed to stop Porcupine", e)
        }
    }

    private fun copyAssetToFile(fileName: String): File {
        val outFile = File(context.filesDir, fileName)
        if (!outFile.exists()) {
            try {
                context.assets.open(fileName).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.e("Porcupine", "Failed to copy asset: \$fileName", e)
            }
        }
        return outFile
    }
}
