package com.behaviorlens.app.analysis

import android.graphics.Bitmap
import com.behaviorlens.app.data.models.PostureScore
import com.behaviorlens.app.data.models.PostureType
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class BodyAnalysisEngine(private val context: android.content.Context) {

    private var poseLandmarker: PoseLandmarker? = null
    private val landmarkHistory = mutableListOf<List<Pair<Float,Float>>>()
    private var handFaceCount = 0
    private var handFaceTimer = 0L

    fun initialize() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()
        val options = PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()
        try {
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            poseLandmarker = null
        }
    }

    suspend fun analyze(bitmap: Bitmap): PostureScore = withContext(Dispatchers.Default) {
        val lm = poseLandmarker ?: return@withContext syntheticScore()
        return@withContext try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = lm.detect(mpImage)
            if (result.landmarks().isEmpty()) return@withContext syntheticScore()

            val landmarks = result.landmarks()[0]
            val pts = landmarks.map { Pair(it.x(), it.y()) }

            val shoulderSym = computeShoulderSymmetry(pts)
            val trunkLean = computeTrunkLean(pts)
            val fidget = computeFidgetingIndex(pts)
            val handFace = detectHandToFace(pts)
            val posture = classifyPosture(shoulderSym, trunkLean, pts)

            landmarkHistory.add(pts)
            if (landmarkHistory.size > 10) landmarkHistory.removeAt(0)

            PostureScore(
                postureType = posture,
                shoulderSymmetry = shoulderSym,
                trunkLeanAngle = trunkLean,
                handToFaceTouches = handFace,
                fidgetingIndex = fidget,
                confidence = 0.8f
            )
        } catch (e: Exception) {
            syntheticScore()
        }
    }

    private fun computeShoulderSymmetry(pts: List<Pair<Float,Float>>): Float {
        if (pts.size < 13) return 1f
        val leftShoulder = pts[11]
        val rightShoulder = pts[12]
        val diff = abs(leftShoulder.second - rightShoulder.second)
        return (1f - (diff * 10f)).coerceIn(0f, 1f)
    }

    private fun computeTrunkLean(pts: List<Pair<Float,Float>>): Float {
        if (pts.size < 25) return 0f
        val leftHip = pts[23]
        val rightHip = pts[24]
        val leftShoulder = pts[11]
        val rightShoulder = pts[12]
        val hipCenterX = (leftHip.first + rightHip.first) / 2f
        val shoulderCenterX = (leftShoulder.first + rightShoulder.first) / 2f
        return atan2(
            shoulderCenterX - hipCenterX,
            abs(leftShoulder.second - leftHip.second).coerceAtLeast(0.001f)
        ) * (180f / PI.toFloat())
    }

    private fun computeFidgetingIndex(pts: List<Pair<Float,Float>>): Float {
        if (landmarkHistory.size < 2) return 0f
        val prev = landmarkHistory.last()
        if (prev.size != pts.size) return 0f
        val velocities = pts.zip(prev).map { (a, b) ->
            sqrt((a.first - b.first).pow(2) + (a.second - b.second).pow(2))
        }
        return velocities.average().toFloat().coerceIn(0f, 1f) * 10f
    }

    private fun detectHandToFace(pts: List<Pair<Float,Float>>): Int {
        if (pts.size < 20) return handFaceCount
        val nose = pts[0]
        val leftWrist = pts[15]
        val rightWrist = pts[16]
        val now = System.currentTimeMillis()
        val leftDist = sqrt((leftWrist.first - nose.first).pow(2) + (leftWrist.second - nose.second).pow(2))
        val rightDist = sqrt((rightWrist.first - nose.first).pow(2) + (rightWrist.second - nose.second).pow(2))
        if ((leftDist < 0.1f || rightDist < 0.1f) && now - handFaceTimer > 2000L) {
            handFaceCount++
            handFaceTimer = now
        }
        return handFaceCount
    }

    private fun classifyPosture(sym: Float, lean: Float, pts: List<Pair<Float,Float>>): PostureType {
        return when {
            sym > 0.85f && abs(lean) < 5f -> PostureType.OPEN
            abs(lean) > 15f -> PostureType.SUBMISSIVE
            sym < 0.6f -> PostureType.DEFENSIVE
            else -> PostureType.NEUTRAL
        }
    }

    private fun syntheticScore() = PostureScore(
        postureType = PostureType.NEUTRAL,
        shoulderSymmetry = 0.85f,
        trunkLeanAngle = 2f,
        handToFaceTouches = 0,
        fidgetingIndex = 0.1f,
        confidence = 0.3f
    )

    fun close() { poseLandmarker?.close() }
}
