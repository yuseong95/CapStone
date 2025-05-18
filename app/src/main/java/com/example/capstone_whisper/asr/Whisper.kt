package com.example.capstone_whisper.asr

import android.content.Context
import com.example.capstone_whisper.engine.WhisperEngine
import java.io.ByteArrayOutputStream

class Whisper(private val context: Context) {
    private val mWhisperEngine : WhisperEngine = WhisperEngine(context)
    private var resultString:String = "Nothing"
    public fun transcribe(wavData:ByteArrayOutputStream) : String{
        val isInit = mWhisperEngine.initialize()
        if(isInit){
            resultString = mWhisperEngine.transcribeFile(wavData)
        }
        return resultString
    }
}