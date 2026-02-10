package com.rimiq.training.model

import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.pose.Pose

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float
)

data class PoseFrame(
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean,
    val pose: Pose,
    val points: Map<Int, LandmarkPoint>
)

data class BallObservation(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val confidence: Float,
    val timestampMs: Long
)

enum class DrillState {
    READY,
    ACTIVE,
    SUMMARY
}

data class DrillMetrics(
    val repCount: Int = 0,
    val hops: Int = 0,
    val stanceCue: String? = null,
    val lateralDistancePx: Float = 0f,
    val poundLow: Int = 0,
    val poundHip: Int = 0,
    val poundHigh: Int = 0,
    val crossoverCount: Int = 0,
    val elapsedMs: Long = 0,
    val debugText: String = ""
)

data class SessionSummary(
    val durationMs: Long,
    val reps: Int,
    val hops: Int,
    val crossoverCount: Int,
    val lowDribbles: Int,
    val hipDribbles: Int,
    val highDribbles: Int
)

object Defaults {
    const val hipKneeAngleThresholdDeg = 145f
    const val lateralYToleranceRatio = 0.14f
    const val hopVelocityThresholdPxPerSec = 560f
    const val minHitConfidence = 0.5f
    val defaultLensFacing = CameraSelector.LENS_FACING_FRONT
}
