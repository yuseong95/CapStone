package com.example.capstone_whisper.engine
import android.content.Context
import android.util.Log
import com.example.capstone_whisper.func.WavFunc
import com.example.capstone_whisper.func.WhisperFunc
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
//import org.tensorflow.lite.gpu.CompatibilityList;
//import org.tensorflow.lite.gpu.GpuDelegate;
//import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

internal class WhisperEngine(private val context: Context) : WhisperEngineInterface {
    private var mIsInitialized = false
    private val TAG = "WhisperEngineJava"
    private var whisperTiny: Interpreter? = null
    private val mWhisperFunc: WhisperFunc = WhisperFunc()
    private val mWavFunc : WavFunc = WavFunc()

    override fun isInitialized(): Boolean {
        return mIsInitialized
    }

    @Throws(IOException::class)
    override fun initialize(): Boolean {
        // Load model
        val tfliteModel = loadModelFile(context, "whisper-small.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors())
        }
        whisperTiny = Interpreter(tfliteModel,options)

        // Load filters and vocab
        val vocabInputStream = context.assets.open("filters_vocab_multilingual.bin")
        val success = mWhisperFunc.loadFiltersAndVocab(vocabInputStream)
        if (success) {
            mIsInitialized = true
            Log.d("성공", "2")
        } else {
            mIsInitialized = false
        }

        return mIsInitialized
    }
    override fun transcribeFile(wavData: ByteArrayOutputStream): String {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...")
        val melSpectrogram = getMelSpectrogram(wavData)
        Log.d(TAG, "Mel spectrogram is calculated...!")

        // Perform inference
        val result: String = runInference(melSpectrogram)
        Log.d(TAG, "Inference is executed...!")

        return result
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getMelSpectrogram(wavData: ByteArrayOutputStream): FloatArray {
        // Get samples in PCM_FLOAT format
        val samples: FloatArray = mWavFunc.getPcmData(wavData)

        val fixedInputSize: Int = 16000 * 30
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength =
            min(samples.size.toDouble(), fixedInputSize.toDouble()).toInt()
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)

        val cores = Runtime.getRuntime().availableProcessors()
        return mWhisperFunc.getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }
    private fun runInference(inputData: FloatArray): String {
        // Create input tensor
        val inputTensor: Tensor = whisperTiny?.getInputTensor(0)
            ?: throw IllegalStateException("Interpreter is not initialized")

        val inputBuffer: TensorBuffer =
            TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType())

        //        printTensorDump("Input Tensor Dump ===>", inputTensor);

        // Create output tensor
        val outputTensor: Tensor = whisperTiny!!.getOutputTensor(0)
        val outputBuffer: TensorBuffer =
            TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)

        //        printTensorDump("Output Tensor Dump ===>", outputTensor);

        // Load input data
        val inputSize =
            inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * java.lang.Float.BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData) {
            inputBuf.putFloat(input)
        }

        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        whisperTiny!!.run(inputBuffer.getBuffer(), outputBuffer.getBuffer())

        // Retrieve the results
        val outputLen: Int = outputBuffer.getIntArray().size
        Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        for (i in 0 until outputLen) {
            val token: Int = outputBuffer.getBuffer().getInt()
            if (token == mWhisperFunc.getTokenEOT()) break

            // Get word for token and Skip additional token
            if (token < mWhisperFunc.getTokenEOT()) {
                val word: String = mWhisperFunc.getWordFromToken(token).toString()
                //Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word)
            } else {
                if (token == mWhisperFunc.getTokenTranscribe()) Log.d(TAG, "It is Transcription...")

                if (token == mWhisperFunc.getTokenTranslate()) Log.d(TAG, "It is Translation...")

                val word: String = mWhisperFunc.getWordFromToken(token).toString()
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }

        return result.toString()
    }
}
