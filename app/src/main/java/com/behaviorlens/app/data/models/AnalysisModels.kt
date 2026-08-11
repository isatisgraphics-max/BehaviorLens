package com.behaviorlens.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Emotion { JOY, SADNESS, ANGER, FEAR, SURPRISE, DISGUST, NEUTRAL }
enum class AnalysisContext { INTERVIEW, NEGOTIATION, PRESENTATION, GENERAL }
enum class PostureType { OPEN, CLOSED, DEFENSIVE, SUBMISSIVE, NEUTRAL }

data class FaceScore(
    val emotions: Map<Emotion, Float> = Emotion.values().associateWith { 0f },
    val isDuchenne: Boolean = false,
    val hasMicroExpression: Boolean = false,
    val confidence: Float = 0f
)

data class GazeMetrics(
    val blinkRate: Float = 0f,
    val gazeDirection: String = "center",
    val gazeBreakDuration: Float = 0f,
    val pupilDilationRatio: Float = 1f,
    val confidence: Float = 0f
)

data class PostureScore(
    val postureType: PostureType = PostureType.NEUTRAL,
    val shoulderSymmetry: Float = 1f,
    val trunkLeanAngle: Float = 0f,
    val handToFaceTouches: Int = 0,
    val fidgetingIndex: Float = 0f,
    val confidence: Float = 0f
)

data class VocalMetrics(
    val fundamentalFrequency: Float = 0f,
    val jitter: Float = 0f,
    val shimmer: Float = 0f,
    val speakingRate: Float = 0f,
    val pauseRatio: Float = 0f,
    val hasVocalTremor: Boolean = false,
    val confidence: Float = 0f
)

data class FusedResult(
    val emotions: Map<Emotion, Float> = Emotion.values().associateWith { 0f },
    val honestyIndex: Float = 50f,
    val honestyCI: Float = 10f,
    val anxietyLevel: Float = 50f,
    val anxietyCI: Float = 10f,
    val engagementScore: Float = 50f,
    val deceptionRisk: Float = 50f
)

data class FrameAnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    val faceScore: FaceScore = FaceScore(),
    val gazeMetrics: GazeMetrics = GazeMetrics(),
    val postureScore: PostureScore = PostureScore(),
    val vocalMetrics: VocalMetrics = VocalMetrics(),
    val fusedResult: FusedResult = FusedResult()
)

data class PeakEvent(
    val timestamp: Long,
    val type: String,
    val value: Float,
    val description: String
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val context: String = AnalysisContext.GENERAL.name,
    val frameCount: Int = 0,
    val avgHonestyIndex: Float = 0f,
    val avgAnxietyLevel: Float = 0f,
    val avgEngagement: Float = 0f,
    val dominantEmotion: String = Emotion.NEUTRAL.name
)
