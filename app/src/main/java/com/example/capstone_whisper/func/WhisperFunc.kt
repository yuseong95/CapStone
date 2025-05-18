package com.example.capstone_whisper.func

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class WhisperFunc {
    private val TAG = "WhisperUtil"
    private var vocab = WhisperVocab()
    private var filters = WhisperFilter()
    private var mel = WhisperMel()
    fun getTokenTranslate(): Int {
        return vocab.tokenTRANSLATE
    }

    fun getTokenTranscribe(): Int {
        return vocab.tokenTRANSCRIBE
    }

    fun getTokenEOT(): Int {
        return vocab.tokenEOT
    }

    fun getTokenSOT(): Int {
        return vocab.tokenSOT
    }

    fun getTokenPREV(): Int {
        return vocab.tokenPREV
    }

    fun getTokenSOLM(): Int {
        return vocab.tokenSOLM
    }

    fun getTokenNOT(): Int {
        return vocab.tokenNOT
    }

    fun getTokenBEG(): Int {
        return vocab.tokenBEG
    }
    fun getWordFromToken(token: Int): String? {
        return vocab.tokenToWord[token]
    }
    @Throws(IOException::class)
    fun loadFiltersAndVocab(vocabStream: InputStream): Boolean {
        // Read vocab file
        val bytes = vocabStream.readBytes()
        val vocabBuf = ByteBuffer.wrap(bytes)
        vocabBuf.order(ByteOrder.nativeOrder())

        // @magic:USEN
        val magic = vocabBuf.getInt()
        if (magic == 0x5553454e) {

        } else {
            return false
        }

        // Load mel filters
        filters.nMel = vocabBuf.getInt()
        filters.nFft = vocabBuf.getInt()
        val filterData = ByteArray(filters.nMel * filters.nFft * java.lang.Float.BYTES)
        vocabBuf[filterData, 0, filterData.size]
        val filterBuf = ByteBuffer.wrap(filterData)
        filterBuf.order(ByteOrder.nativeOrder())

        filters.data = FloatArray(filters.nMel * filters.nFft)
        run {
            var i= 0
            while (filterBuf.hasRemaining()) {
                filters.data[i] = filterBuf.getFloat()
                i++
            }
        }

        // Load vocabulary
        val nVocab = vocabBuf.getInt()
        for (i in 0 until nVocab) {
            val len = vocabBuf.getInt()
            val wordBytes = ByteArray(len)
            vocabBuf[wordBytes, 0, wordBytes.size]
            val word = String(wordBytes)
            vocab.tokenToWord.put(i, word)
        }

        // Add additional vocab ids
        val nVocabAdditional: Int
        nVocabAdditional = vocab.nVocabMultilingual
        vocab.tokenEOT++
        vocab.tokenSOT++
        vocab.tokenPREV++
        vocab.tokenSOLM++
        vocab.tokenNOT++
        vocab.tokenBEG++


        for (i in nVocab until nVocabAdditional) {
            var word = if (i > vocab.tokenBEG) {
                "[_TT_" + (i - vocab.tokenBEG) + "]"
            } else if (i == vocab.tokenEOT) {
                "[_EOT_]"
            } else if (i == vocab.tokenSOT) {
                "[_SOT_]"
            } else if (i == vocab.tokenPREV) {
                "[_PREV_]"
            } else if (i == vocab.tokenNOT) {
                "[_NOT_]"
            } else if (i == vocab.tokenBEG) {
                "[_BEG_]"
            } else {
                "[_extra_token_$i]"
            }

            vocab.tokenToWord.put(i, word)
            //Log.d(TAG, "i= " + i + ", word= " + word);
        }

        return true
    }
    fun getMelSpectrogram(samples: FloatArray, nSamples: Int, nThreads: Int): FloatArray {
        val fftSize: Int = 400
        val fftStep: Int = 160

        mel.nMel = 80
        mel.nLen = nSamples / fftStep
        mel.data = FloatArray(mel.nMel * mel.nLen)

        val hann = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            hann[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()
        }

        val nFft = 1 + fftSize / 2

        /////////////// UNCOMMENT below block to use multithreaded mel calculation /////////////////////////
        // Calculate mel values using multiple threads
        val workers: MutableList<Thread> = ArrayList()
        for (iw in 0 until nThreads) {
            val ith = iw // Capture iw in a final variable for use in the lambda
            val thread = Thread {
                // Inside the thread, ith will have the same value as iw (first value is 0)
                val fftIn = FloatArray(fftSize)
                Arrays.fill(fftIn, 0.0f)
                val fftOut = FloatArray(fftSize * 2)

                var i = ith
                while (i < mel.nLen) {
                    /////////////// END of Block ///////////////////////////////////////////////////////////////////////

/////////////// COMMENT below block to use multithreaded mel calculation ///////////////////////////
//        float[] fftIn = new float[fftSize];
//        Arrays.fill(fftIn, 0.0f);
//        float[] fftOut = new float[fftSize * 2];
//
//        for (int i = 0; i < mel.nLen; i++) {
/////////////// END of Block ///////////////////////////////////////////////////////////////////////
                    val offset = i * fftStep

                    // apply Hanning window
                    for (j in 0 until fftSize) {
                        if (offset + j < nSamples) {
                            fftIn[j] = hann[j] * samples[offset + j]
                        } else {
                            fftIn[j] = 0.0f
                        }
                    }

                    // FFT -> mag^2
                    fft(fftIn, fftOut)
                    for (j in 0 until fftSize) {
                        fftOut[j] =
                            fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
                    }

                    for (j in 1 until fftSize / 2) {
                        fftOut[j] += fftOut[fftSize - j]
                    }

                    // mel spectrogram
                    for (j in 0 until mel.nMel) {
                        var sum = 0.0
                        for (k in 0 until nFft) {
                            sum += (fftOut[k] * filters.data.get(j * nFft + k)).toDouble()
                        }

                        if (sum < 1e-10) {
                            sum = 1e-10
                        }

                        sum = log10(sum)
                        mel.data[(j * mel.nLen + i)] = sum.toFloat()
                    }
                    i += nThreads
                }
            }
            workers.add(thread)
            thread.start()
        }

        // Wait for all threads to finish
        for (worker in workers) {
            try {
                worker.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        /////////////// END of Block ///////////////////////////////////////////////////////////////////////

        // clamping and normalization
        var mmax = -1e20
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data.get(i) > mmax) {
                mmax = mel.data.get(i).toDouble()
            }
        }

        mmax -= 8.0
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data.get(i) < mmax) {
                mel.data[i] = mmax.toFloat()
            }
            mel.data[i] = ((mel.data.get(i) + 4.0) / 4.0).toFloat()
        }

        return mel.data
    }
    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0 until inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0 until inSize) {
                val angle = (2 * Math.PI * k * n / inSize).toFloat()
                re += (input[n] * cos(angle.toDouble())).toFloat()
                im -= (input[n] * sin(angle.toDouble())).toFloat()
            }
            output[k * 2 + 0] = re
            output[k * 2 + 1] = im
        }
    }
    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }

        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)

        var indxEven = 0
        var indxOdd = 0
        for (i in 0 until inSize) {
            if (i % 2 == 0) {
                even[indxEven] = input[i]
                indxEven++
            } else {
                odd[indxOdd] = input[i]
                indxOdd++
            }
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)

        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0 until inSize / 2) {
            val theta = (2 * Math.PI * k / inSize).toFloat()
            val re = cos(theta.toDouble()).toFloat()
            val im = -sin(theta.toDouble()).toFloat()
            val reOdd = oddFft[2 * k + 0]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }
    private class WhisperVocab {
        var golden_generated_ids: IntArray = intArrayOf(
            50257, 50362, 1770, 13, 2264, 346, 353, 318,
            262, 46329, 286, 262, 3504, 6097, 11, 290, 356, 389, 9675, 284, 7062
        )

        // Token types
        var tokenEOT: Int = 50256 // end of transcript
        var tokenSOT: Int = 50257 // start of transcript
        var tokenPREV: Int = 50360
        var tokenSOLM: Int = 50361 // ??
        var tokenNOT: Int = 50362 // no timestamps
        var tokenBEG: Int = 50363

        // Available tasks
        val tokenTRANSLATE: Int = 50358
        val tokenTRANSCRIBE: Int = 50359

        // Vocab types
        val nVocabEnglish: Int = 51864 // for english only vocab
        val nVocabMultilingual: Int = 51865 // for multilingual vocab
        val tokenToWord: MutableMap<Int, String> = mutableMapOf()
    }

    private class WhisperFilter {
        var nMel: Int = 0
        var nFft: Int = 0
        lateinit var data: FloatArray
    }

    private class WhisperMel {
        var nLen: Int = 0
        var nMel: Int = 0
        lateinit var data: FloatArray
    }

    private class InputLang private constructor(var name: String, var code: String, var id: Long) {
        val langList: java.util.ArrayList<InputLang>
            // Initialize the list of input language objects
            get() {
                val inputLangList = java.util.ArrayList<InputLang>()
                inputLangList.add(InputLang("English", "en", 50259))
                inputLangList.add(InputLang("Spanish", "es", 50262))
                inputLangList.add(InputLang("Hindi", "hi", 50276))
                inputLangList.add(InputLang("Telugu", "te", 50299))
                return inputLangList
            }
    }
}