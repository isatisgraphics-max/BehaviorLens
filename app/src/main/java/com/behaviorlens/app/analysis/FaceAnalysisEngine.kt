package com.behaviorlens.app.analysis

import android.content.Context
import android.graphics.Bitmap
import com.behaviorlens.app.data.models.Emotion
import com.behaviorlens.app.data.models.FaceScore
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FaceAnalysisEngine(private val context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private var lastBlendshapes: Map<String, Float> = emptyMap()
    private var prevBlendshapes: Map<String, Float> = emptyMap()
    private var microExpressionTimer = 0L

    fun initialize() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()
        val options = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setOutputFaceBlendshapes(true)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()
        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            faceLandmarker = null
        }
    }

    suspend fun analyze(bitmap: Bitmap): FaceScore = withContext(Dispatchers.Default) {
        val lm = faceLandmarker ?: return@withContext syntheticScore()
        return@withContext try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = lm.detect(mpImage)
            if (result.faceBlendshapes().isEmpty || result.faceBlendshapes().get().isEmpty()) {
                return@withContext syntheticScore()
            }
            val blendshapes = result.faceBlendshapes().get()[0]
                .associateBy({ it.categoryName() }, { it.score() })

            prevBlendshapes = lastBlendshapes
            lastBlendshapes = blendshapes

            val emotions = classifyEmotions(blendshapes)
            val isDuchenne = isDuchenneSmile(blendshapes)
            val hasMicro = detectMicroExpression(blendshapes, prevBlendshapes)

            FaceScore(
                emotions = emotions,
                isDuchenne = isDuchenne,
                hasMicroExpression = hasMicro,
                confidence = blendshapes["_neutral"]?.let { 1f - it } ?: 0.5f
            )
        } catch (e: Exception) {
            syntheticScore()
        }
    }

    private fun classifyEmotions(bs: Map<String, Float>): Map<Emotion, Float> {
        val joy = ((bs["mouthSmileLeft"] ?: 0f) + (bs["mouthSmileRight"] ?: 0f)) / 2f
        val sadness = ((bs["mouthFrownLeft"] ?: 0f) + (bs["mouthFrownRight"] ?: 0f) +
                (bs["browDownLeft"] ?: 0f)) / 3f
        val anger = ((bs["browDownLeft"] ?: 0f) + (bs["browDownRight"] ?: 0f) +
                (bs["noseSneerLeft"] ?: 0f)) / 3f
        val fear = ((bs["eyeWideLeft"] ?: 0f) + (bs["eyeWideRight"] ?: 0f) +
                (bs["browInnerUp"] ?: 0f)) / 3f
        val surprise = ((bs["jawOpen"] ?: 0f) + (bs["eyeWideLeft"] ?: 0f) +
                (bs["eyeWideRight"] ?: 0f)) / 3f
        val disgust = ((bs["noseSneerLeft"] ?: 0f) + (bs["noseSneerRight"] ?: 0f) +
                (bs["mouthUpperUpLeft"] ?: 0f)) / 3f

        val raw = mapOf(
            Emotion.JOY to joy, Emotion.SADNESS to sadness, Emotion.ANGER to anger,
            Emotion.FEAR to fear, Emotion.SURPRISE to surprise, Emotion.DISGUST to disgust
        )
        val total = raw.values.sum().coerceAtLeast(0.001f)
        val neutral = (1f - total).coerceIn(0f, 1f)
        val withNeutral = raw.toMutableMap().apply { put(Emotion.NEUTRAL, neutral) }
        val newTotal = withNeutral.values.sum()
        return withNeutral.mapValues { (it.value / newTotal).coerceIn(0f, 1f) }
    }

    private fun isDuchenneSmile(bs: Map<String, Float>): Boolean {
        val smileL = bs["mouthSmileLeft"] ?: 0f
        val smileR = bs["mouthSmileRight"] ?: 0f
        val cheekL = bs["cheekSquintLeft"] ?: 0f
        val cheekR = bs["cheekSquintRight"] ?: 0f
        return (smileL + smileR) / 2f > 0.3f && (cheekL + cheekR) / 2f > 0.2f
    }

    private fun detectMicroExpression(current: Map<String, Float>, prev: Map<String, Float>): Boolean {
        if (prev.isEmpty()) return false
        val now = System.currentTimeMillis()
        val keys = listOf("mouthSmileLeft", "browDownLeft", "eyeWideLeft", "jawOpen")
        val delta = keys.sumOf { abs((current[it] ?: 0f) - (prev[it] ?: 0f)).toDouble() }.toFloat()
        if (delta > 0.4f && now - microExpressionTimer > 500L) {
            microExpressionTimer = now
            return true
        }
        return false
    }

    private fun syntheticScore(): FaceScore = FaceScore(
        emotions = mapOf(
            Emotion.NEUTRAL to 0.6f, Emotion.JOY to 0.15f, Emotion.SADNESS to 0.08f,
            Emotion.ANGER to 0.05f, Emotion.FEAR to 0.05f, Emotion.SURPRISE to 0.04f,
            Emotion.DISGUST to 0.03f
        ),
        confidence = 0.3f
    )

    fun close() { faceLandmarker?.close() }
}
