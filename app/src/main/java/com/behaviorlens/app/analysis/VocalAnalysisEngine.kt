package com.behaviorlens.app.analysis

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.behaviorlens.app.data.models.VocalMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class VocalAnalysisEngine {

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)
    private val fftSize = 2048
    private val f0History = mutableListOf<Float>()
    private val pauseHistory = mutableListOf<Boolean>()

    fun startRecording() {
        stopRecording()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()
    }

    fun stopRecording() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    suspend fun analyze(): VocalMetrics = withContext(Dispatchers.Default) {
        val ar = audioRecord ?: return@withContext syntheticMetrics()
        val buffer = ShortArray(fftSize)
        val bytesRead = ar.read(buffer, 0, fftSize)
        if (bytesRead <= 0) return@withContext syntheticMetrics()

        val samples = FloatArray(fftSize) { buffer[it].toFloat() / 32768f }
        val rms = computeRMS(samples)
        val isSilent = rms < 0.01f
        pauseHistory.add(isSilent)
        if (pauseHistory.size > 100) pauseHistory.removeAt(0)

        val f0 = if (!isSilent) computeF0(samples) else 0f
        if (f0 > 50f && f0 < 500f) {
            f0History.add(f0)
            if (f0History.size > 50) f0History.removeAt(0)
        }

        val jitter = computeJitter()
        val shimmer = computeShimmer(samples)
        val pauseRatio = pauseHistory.count { it }.toFloat() / pauseHistory.size.toFloat().coerceAtLeast(1f)
        val hasTremor = detectTremor()
        val speakingRate = estimateSpeakingRate()
        val avgF0 = if (f0History.isNotEmpty()) f0History.average().toFloat() else 0f

        VocalMetrics(
            fundamentalFrequency = avgF0,
            jitter = jitter,
            shimmer = shimmer,
            speakingRate = speakingRate,
            pauseRatio = pauseRatio,
            hasVocalTremor = hasTremor,
            confidence = if (isSilent) 0.1f else 0.7f
        )
    }

    private fun computeRMS(samples: FloatArray): Float =
        sqrt(samples.map { it * it }.average()).toFloat()

    private fun computeF0(samples: FloatArray): Float {
        val real = samples.copyOf(fftSize)
        val imag = FloatArray(fftSize)
        fft(real, imag)
        val magnitudes = FloatArray(fftSize / 2) { sqrt(real[it].pow(2) + imag[it].pow(2)) }
        val minBin = (50f * fftSize / sampleRate).toInt()
        val maxBin = (500f * fftSize / sampleRate).toInt().coerceAtMost(fftSize / 2 - 1)
        var maxMag = 0f; var peakBin = minBin
        for (i in minBin..maxBin) {
            if (magnitudes[i] > maxMag) { maxMag = magnitudes[i]; peakBin = i }
        }
        return (peakBin.toFloat() * sampleRate / fftSize).coerceIn(50f, 500f)
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { real[i] = real[j].also { real[j] = real[i] }; imag[i] = imag[j].also { imag[j] = imag[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wRe = cos(ang).toFloat(); val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f; var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = real[i + k]; val uIm = imag[i + k]
                    val vRe = real[i + k + len/2] * curRe - imag[i + k + len/2] * curIm
                    val vIm = real[i + k + len/2] * curIm + imag[i + k + len/2] * curRe
                    real[i + k] = uRe + vRe; imag[i + k] = uIm + vIm
                    real[i + k + len/2] = uRe - vRe; imag[i + k + len/2] = uIm - vIm
                    val newRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe; curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun computeJitter(): Float {
        if (f0History.size < 3) return 0f
        val periods = f0History.map { 1f / it }
        val diffs = (1 until periods.size).map { abs(periods[it] - periods[it-1]) }
        val avgPeriod = periods.average().toFloat()
        return (diffs.average().toFloat() / avgPeriod).coerceIn(0f, 1f) * 100f
    }

    private fun computeShimmer(samples: FloatArray): Float {
        val frameSize = 256
        val rmsValues = (0 until samples.size / frameSize).map { i ->
            computeRMS(samples.copyOfRange(i * frameSize, (i + 1) * frameSize))
        }
        if (rmsValues.size < 2) return 0f
        val diffs = (1 until rmsValues.size).map { abs(rmsValues[it] - rmsValues[it-1]) }
        val avgRms = rmsValues.average().toFloat().coerceAtLeast(0.001f)
        return (diffs.average().toFloat() / avgRms).coerceIn(0f, 1f) * 100f
    }

    private fun detectTremor(): Boolean {
        if (f0History.size < 10) return false
        val variance = f0History.map { (it - f0History.average()).pow(2) }.average()
        return variance > 25.0
    }

    private fun estimateSpeakingRate(): Float {
        if (pauseHistory.size < 10) return 0f
        var transitions = 0
        for (i in 1 until pauseHistory.size) {
            if (pauseHistory[i] != pauseHistory[i-1]) transitions++
        }
        return transitions.toFloat() / (pauseHistory.size / 10f)
    }

    private fun syntheticMetrics() = VocalMetrics(
        fundamentalFrequency = 120f,
        jitter = 1.5f,
        shimmer = 3f,
        speakingRate = 3.5f,
        pauseRatio = 0.2f,
        hasVocalTremor = false,
        confidence = 0.3f
    )
}
