package com.example.capstone_whisper.engine

import java.io.ByteArrayOutputStream
import java.io.IOException

interface WhisperEngineInterface {
    fun isInitialized(): Boolean
    @Throws(IOException::class)
    fun initialize(): Boolean
    fun transcribeFile(wavData: ByteArrayOutputStream): String?
}
