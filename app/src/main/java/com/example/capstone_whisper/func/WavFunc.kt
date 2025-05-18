package com.example.capstone_whisper.func

import java.io.ByteArrayOutputStream

class WavFunc {
    public fun getPcmData(pcmStream: ByteArrayOutputStream): FloatArray {
        val bytes = pcmStream.toByteArray()
        val sampleCount = bytes.size / 2
        return FloatArray(sampleCount) { i ->
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            ((hi shl 8) or lo) / 32768.0f
        }
    }
}