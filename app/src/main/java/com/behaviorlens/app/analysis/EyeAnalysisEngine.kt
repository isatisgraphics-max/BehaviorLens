package com.behaviorlens.app.analysis

import android.graphics.Bitmap
import com.behaviorlens.app.data.models.GazeMetrics
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class EyeAnalysisEngine {

    private val blinkHistory = mutableListOf<Long>()
    private var lastBlinkTime = 0L
    private var lastEarLeft = 1f
    private var lastEarRight = 1f
    private var gazeBreakStart = 0L
    private var currentGazeBreakDuration = 0f

    suspend fun analyze(bitmap: Bitmap, landmarker: FaceLandmarker?): GazeMetrics =
        withContext(Dispatchers.Default) {
            val lm = landmarker ?: return@withContext syntheticMetrics()
            return@withContext try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = lm.detect(mpImage)
                if (result.faceLandmarks().isEmpty()) return@withContext syntheticMetrics()

                val landmarks = result.faceLandmarks()[0]
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()

                val earLeft = computeEAR(landmarks, w, h,
                    leftIdx = listOf(362, 385, 387, 263, 373, 380))
                val earRight = computeEAR(landmarks, w, h,
                    leftIdx = listOf(33, 160, 158, 133, 153, 144))

                val blinkDetected = detectBlink(earLeft, earRight)
                val blinkRate = computeBlinkRate()
                val gazeDir = computeGazeDirection(landmarks, w, h)
                val gazeBreak = updateGazeBreak(gazeDir)
                val pupilRatio = computePupilRatio(landmarks, w, h)

                lastEarLeft = earLeft
                lastEarRight = earRight

                GazeMetrics(
                    blinkRate = blinkRate,
                    gazeDirection = gazeDir,
                    gazeBreakDuration = gazeBreak,
                    pupilDilationRatio = pupilRatio,
                    confidence = 0.75f
                )
            } catch (e: Exception) {
                syntheticMetrics()
            }
        }

    private fun computeEAR(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        w: Float, h: Float, leftIdx: List<Int>
    ): Float {
        if (landmarks.size <= leftIdx.max()) return 1f
        fun pt(i: Int) = Pair(landmarks[i].x() * w, landmarks[i].y() * h)
        fun dist(a: Pair<Float,Float>, b: Pair<Float,Float>) =
            sqrt((a.first-b.first)*(a.first-b.first) + (a.second-b.second)*(a.second-b.second))

        val v1 = dist(pt(leftIdx[1]), pt(leftIdx[5]))
        val v2 = dist(pt(leftIdx[2]), pt(leftIdx[4]))
        val h1 = dist(pt(leftIdx[0]), pt(leftIdx[3]))
        return (v1 + v2) / (2f * h1 + 0.001f)
    }

    private fun detectBlink(earL: Float, earR: Float): Boolean {
        val avgEar = (earL + earR) / 2f
        val now = System.currentTimeMillis()
        return if (avgEar < 0.2f && lastEarLeft >= 0.2f && now - lastBlinkTime > 150L) {
            blinkHistory.add(now)
            lastBlinkTime = now
            if (blinkHistory.size > 30) blinkHistory.removeAt(0)
            true
        } else false
    }

    private fun computeBlinkRate(): Float {
        val now = System.currentTimeMillis()
        val recent = blinkHistory.filter { now - it < 60_000L }
        return recent.size.toFloat()
    }

    private fun computeGazeDirection(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        w: Float, h: Float
    ): String {
        if (landmarks.size < 468) return "center"
        val noseTip = landmarks[4]
        val leftEye = landmarks[33]
        val rightEye = landmarks[263]
        val eyeCenterX = (leftEye.x() + rightEye.x()) / 2f
        val diff = noseTip.x() - eyeCenterX
        return when {
            diff < -0.05f -> "left"
            diff > 0.05f -> "right"
            noseTip.y() < leftEye.y() - 0.05f -> "up"
            noseTip.y() > leftEye.y() + 0.05f -> "down"
            else -> "center"
        }
    }

    private fun updateGazeBreak(direction: String): Float {
        val now = System.currentTimeMillis()
        return if (direction != "center") {
            if (gazeBreakStart == 0L) gazeBreakStart = now
            ((now - gazeBreakStart) / 1000f).also {
                currentGazeBreakDuration = it
            }
        } else {
            gazeBreakStart = 0L
            currentGazeBreakDuration = 0f
            0f
        }
    }

    private fun computePupilRatio(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        w: Float, h: Float
    ): Float {
        if (landmarks.size < 468) return 1f
        val irisLeft = landmarks[468]
        val irisRight = if (landmarks.size > 473) landmarks[473] else return 1f
        val leftCornerOuter = landmarks[33]
        val leftCornerInner = landmarks[133]
        val eyeWidth = abs(leftCornerOuter.x() - leftCornerInner.x()).coerceAtLeast(0.001f)
        return ((irisLeft.x() - leftCornerOuter.x()) / eyeWidth).coerceIn(0.3f, 3f)
    }

    private fun syntheticMetrics() = GazeMetrics(
        blinkRate = 15f,
        gazeDirection = "center",
        gazeBreakDuration = 0f,
        pupilDilationRatio = 1f,
        confidence = 0.3f
    )
}
