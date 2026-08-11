package com.behaviorlens.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.behaviorlens.app.analysis.*
import com.behaviorlens.app.data.db.AppDatabase
import com.behaviorlens.app.data.models.*
import com.behaviorlens.app.data.repository.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val faceEngine = FaceAnalysisEngine(application)
    private val eyeEngine = EyeAnalysisEngine()
    private val bodyEngine = BodyAnalysisEngine(application)
    private val vocalEngine = VocalAnalysisEngine()
    private val fusionEngine = FusionEngine()
    private val repository = SessionRepository(AppDatabase.getInstance(application))

    private val _currentResult = MutableStateFlow<FrameAnalysisResult?>(null)
    val currentResult: StateFlow<FrameAnalysisResult?> = _currentResult

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _sessionResults = MutableStateFlow<List<FrameAnalysisResult>>(emptyList())
    val sessionResults: StateFlow<List<FrameAnalysisResult>> = _sessionResults

    private val _selectedContext = MutableStateFlow(AnalysisContext.GENERAL)
    val selectedContext: StateFlow<AnalysisContext> = _selectedContext

    private val resultBuffer = mutableListOf<FrameAnalysisResult>()
    private var sessionStartTime = 0L
    private var analysisJob: Job? = null

    fun initialize() {
        viewModelScope.launch(Dispatchers.Default) {
            faceEngine.initialize()
            bodyEngine.initialize()
        }
    }

    fun setContext(context: AnalysisContext) { _selectedContext.value = context }

    fun startLiveAnalysis() {
        if (_isAnalyzing.value) return
        _isAnalyzing.value = true
        sessionStartTime = System.currentTimeMillis()
        resultBuffer.clear()
        vocalEngine.startRecording()
    }

    fun stopLiveAnalysis() {
        _isAnalyzing.value = false
        vocalEngine.stopRecording()
        analysisJob?.cancel()
        saveSession()
    }

    fun analyzeFrame(bitmap: Bitmap) {
        if (!_isAnalyzing.value) return
        viewModelScope.launch(Dispatchers.Default) {
            val faceDeferred = async { faceEngine.analyze(bitmap) }
            val gazeDeferred = async { eyeEngine.analyze(bitmap, null) }
            val bodyDeferred = async { bodyEngine.analyze(bitmap) }
            val vocalDeferred = async { vocalEngine.analyze() }

            val face = faceDeferred.await()
            val gaze = gazeDeferred.await()
            val body = bodyDeferred.await()
            val vocal = vocalDeferred.await()

            val fused = fusionEngine.fuse(
                face, gaze, body, vocal, _selectedContext.value
            )
            val frame = FrameAnalysisResult(
                timestamp = System.currentTimeMillis(),
                faceScore = face,
                gazeMetrics = gaze,
                postureScore = body,
                vocalMetrics = vocal,
                fusedResult = fused
            )
            resultBuffer.add(frame)
            _currentResult.value = frame
            _sessionResults.value = resultBuffer.toList()
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap): FrameAnalysisResult =
        withContext(Dispatchers.Default) {
            val face = faceEngine.analyze(bitmap)
            val gaze = eyeEngine.analyze(bitmap, null)
            val body = bodyEngine.analyze(bitmap)
            val fused = fusionEngine.fuse(face, gaze, body, VocalMetrics(), _selectedContext.value)
            FrameAnalysisResult(
                faceScore = face, gazeMetrics = gaze,
                postureScore = body, fusedResult = fused
            ).also { _currentResult.value = it }
        }

    private fun saveSession() {
        if (resultBuffer.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val avgFused = FusedResult(
                honestyIndex = resultBuffer.map { it.fusedResult.honestyIndex }.average().toFloat(),
                anxietyLevel = resultBuffer.map { it.fusedResult.anxietyLevel }.average().toFloat(),
                engagementScore = resultBuffer.map { it.fusedResult.engagementScore }.average().toFloat(),
                deceptionRisk = resultBuffer.map { it.fusedResult.deceptionRisk }.average().toFloat()
            )
            val dominantEmotion = resultBuffer
                .flatMap { it.fusedResult.emotions.entries }
                .groupBy { it.key }
                .mapValues { e -> e.value.sumOf { it.value.toDouble() } / e.value.size }
                .maxByOrNull { it.value }?.key ?: Emotion.NEUTRAL

            repository.saveSession(SessionEntity(
                startTime = sessionStartTime,
                duration = System.currentTimeMillis() - sessionStartTime,
                context = _selectedContext.value.name,
                frameCount = resultBuffer.size,
                avgHonestyIndex = avgFused.honestyIndex,
                avgAnxietyLevel = avgFused.anxietyLevel,
                avgEngagement = avgFused.engagementScore,
                dominantEmotion = dominantEmotion.name
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceEngine.close()
        bodyEngine.close()
        vocalEngine.stopRecording()
    }
}
