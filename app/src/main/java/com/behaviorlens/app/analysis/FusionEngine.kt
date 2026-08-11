package com.behaviorlens.app.analysis

import com.behaviorlens.app.data.models.*

class FusionEngine {

    data class Weights(
        val face: Float = 0.40f,
        val eyes: Float = 0.25f,
        val body: Float = 0.20f,
        val voice: Float = 0.15f
    )

    private val contextWeights = mapOf(
        AnalysisContext.INTERVIEW to Weights(0.35f, 0.30f, 0.20f, 0.15f),
        AnalysisContext.NEGOTIATION to Weights(0.30f, 0.25f, 0.25f, 0.20f),
        AnalysisContext.PRESENTATION to Weights(0.40f, 0.20f, 0.25f, 0.15f),
        AnalysisContext.GENERAL to Weights(0.40f, 0.25f, 0.20f, 0.15f)
    )

    fun fuse(
        face: FaceScore,
        gaze: GazeMetrics,
        posture: PostureScore,
        vocal: VocalMetrics,
        context: AnalysisContext = AnalysisContext.GENERAL
    ): FusedResult {
        val w = contextWeights[context] ?: Weights()

        val emotions = fuseEmotions(face, w)
        val honestyIndex = computeHonesty(face, gaze, posture, vocal, w)
        val anxietyLevel = computeAnxiety(face, gaze, posture, vocal, w)
        val engagementScore = computeEngagement(face, gaze, posture, vocal, w)
        val deceptionRisk = computeDeceptionRisk(face, gaze, posture, vocal, w)

        val confidenceAvg = (face.confidence * w.face + gaze.confidence * w.eyes +
                posture.confidence * w.body + vocal.confidence * w.voice)

        val ci = (1f - confidenceAvg) * 15f

        return FusedResult(
            emotions = emotions,
            honestyIndex = honestyIndex,
            honestyCI = ci,
            anxietyLevel = anxietyLevel,
            anxietyCI = ci * 0.8f,
            engagementScore = engagementScore,
            deceptionRisk = deceptionRisk
        )
    }

    private fun fuseEmotions(face: FaceScore, w: Weights): Map<Emotion, Float> {
        val total = face.emotions.values.sum().coerceAtLeast(0.001f)
        return face.emotions.mapValues { (it.value / total).coerceIn(0f, 1f) }
    }

    private fun computeHonesty(
        face: FaceScore, gaze: GazeMetrics, posture: PostureScore, vocal: VocalMetrics, w: Weights
    ): Float {
        val faceHonesty = (if (face.isDuchenne) 0.8f else 0.4f) *
                (1f - (face.emotions[Emotion.DISGUST] ?: 0f))
        val gazeHonesty = when (gaze.gazeDirection) {
            "center" -> 0.8f
            else -> (0.8f - gaze.gazeBreakDuration * 0.1f).coerceIn(0.2f, 0.8f)
        } * (1f - (gaze.blinkRate - 15f).coerceIn(0f, 20f) / 40f)
        val bodyHonesty = when (posture.postureType) {
            PostureType.OPEN -> 0.8f
            PostureType.CLOSED, PostureType.DEFENSIVE -> 0.3f
            else -> 0.6f
        } * (1f - posture.fidgetingIndex * 0.1f).coerceIn(0.1f, 1f)
        val vocalHonesty = (1f - vocal.jitter / 100f) *
                (if (vocal.hasVocalTremor) 0.5f else 0.9f)

        val score = (faceHonesty * w.face + gazeHonesty * w.eyes +
                bodyHonesty * w.body + vocalHonesty * w.voice)
        return (score * 100f).coerceIn(0f, 100f)
    }

    private fun computeAnxiety(
        face: FaceScore, gaze: GazeMetrics, posture: PostureScore, vocal: VocalMetrics, w: Weights
    ): Float {
        val faceAnxiety = ((face.emotions[Emotion.FEAR] ?: 0f) +
                (face.emotions[Emotion.SURPRISE] ?: 0f) * 0.5f).coerceIn(0f, 1f)
        val gazeAnxiety = (gaze.blinkRate - 10f).coerceIn(0f, 30f) / 30f +
                gaze.gazeBreakDuration.coerceIn(0f, 5f) / 5f * 0.5f
        val bodyAnxiety = posture.fidgetingIndex.coerceIn(0f, 1f) +
                posture.handToFaceTouches.toFloat().coerceIn(0f, 5f) / 5f * 0.5f
        val vocalAnxiety = vocal.jitter / 100f * 0.5f +
                if (vocal.hasVocalTremor) 0.5f else 0f

        val score = (faceAnxiety * w.face + gazeAnxiety * w.eyes +
                bodyAnxiety * w.body + vocalAnxiety * w.voice)
        return (score * 100f).coerceIn(0f, 100f)
    }

    private fun computeEngagement(
        face: FaceScore, gaze: GazeMetrics, posture: PostureScore, vocal: VocalMetrics, w: Weights
    ): Float {
        val faceEngagement = ((face.emotions[Emotion.JOY] ?: 0f) +
                (face.emotions[Emotion.SURPRISE] ?: 0f) * 0.5f).coerceIn(0f, 1f)
        val gazeEngagement = if (gaze.gazeDirection == "center") 0.9f else 0.4f
        val bodyEngagement = when (posture.postureType) {
            PostureType.OPEN -> 0.9f
            PostureType.SUBMISSIVE -> 0.4f
            else -> 0.6f
        }
        val vocalEngagement = (vocal.speakingRate / 5f).coerceIn(0f, 1f)

        val score = (faceEngagement * w.face + gazeEngagement * w.eyes +
                bodyEngagement * w.body + vocalEngagement * w.voice)
        return (score * 100f).coerceIn(0f, 100f)
    }

    private fun computeDeceptionRisk(
        face: FaceScore, gaze: GazeMetrics, posture: PostureScore, vocal: VocalMetrics, w: Weights
    ): Float {
        val microExprRisk = if (face.hasMicroExpression) 0.7f else 0f
        val nonDuchenneRisk = if (!face.isDuchenne &&
            (face.emotions[Emotion.JOY] ?: 0f) > 0.3f) 0.5f else 0f
        val gazeRisk = gaze.gazeBreakDuration.coerceIn(0f, 5f) / 5f * 0.8f
        val postureRisk = when (posture.postureType) {
            PostureType.CLOSED, PostureType.DEFENSIVE -> 0.6f
            else -> 0f
        } + posture.fidgetingIndex.coerceIn(0f, 1f) * 0.4f
        val vocalRisk = vocal.jitter / 100f + if (vocal.hasVocalTremor) 0.5f else 0f

        val composite = (maxOf(microExprRisk, nonDuchenneRisk) * w.face +
                gazeRisk * w.eyes + postureRisk * w.body + vocalRisk * w.voice)
        return (composite * 100f).coerceIn(0f, 100f)
    }
}
