package com.rimiq.training.drills

import com.google.mlkit.vision.pose.PoseLandmark
import com.rimiq.training.model.BallObservation
import com.rimiq.training.model.Defaults
import com.rimiq.training.model.DrillMetrics
import com.rimiq.training.model.DrillState
import com.rimiq.training.model.PoseFrame
import com.rimiq.training.model.SessionSummary
import com.rimiq.training.util.Geometry
import kotlin.math.abs

class DrillEngine {
    private var state: DrillState = DrillState.READY
    private var metrics: DrillMetrics = DrillMetrics()
    private var startedAtMs: Long = 0L

    private var previousMidHipX: Float? = null
    private var previousHipY: Float? = null
    private var previousTimestamp: Long = 0L
    private var prevVerticalVelocity: Float = 0f

    private var previousBallSide: Side = Side.UNKNOWN
    private var handStrikeDetected = false

    fun consume(poseFrame: PoseFrame, ballObservation: BallObservation?): Pair<DrillState, DrillMetrics> {
        val now = poseFrame.timestampMs
        state = when {
            !isUserInFrame(poseFrame) -> DrillState.READY
            state == DrillState.SUMMARY -> DrillState.SUMMARY
            else -> DrillState.ACTIVE
        }

        if (state == DrillState.READY) {
            resetLiveMetrics()
            return state to metrics
        }

        if (startedAtMs == 0L) startedAtMs = now

        val stanceCue = evaluateDefensiveStance(poseFrame)
        val lateralDistance = evaluateLateralSlides(poseFrame)
        val hopCountIncrement = evaluateJumpCadence(poseFrame)

        val dribbleZone = evaluatePoundDribbleZone(poseFrame, ballObservation)
        val crossoverIncrement = evaluateCrossover(poseFrame, ballObservation)

        metrics = metrics.copy(
            stanceCue = stanceCue,
            lateralDistancePx = lateralDistance,
            hops = metrics.hops + hopCountIncrement,
            poundLow = metrics.poundLow + if (dribbleZone == DribbleZone.LOW) 1 else 0,
            poundHip = metrics.poundHip + if (dribbleZone == DribbleZone.HIP) 1 else 0,
            poundHigh = metrics.poundHigh + if (dribbleZone == DribbleZone.HIGH) 1 else 0,
            crossoverCount = metrics.crossoverCount + crossoverIncrement,
            repCount = metrics.repCount + hopCountIncrement + crossoverIncrement,
            elapsedMs = now - startedAtMs,
            debugText = "state=$state zone=$dribbleZone"
        )

        return state to metrics
    }

    fun finishSession(): SessionSummary {
        state = DrillState.SUMMARY
        return SessionSummary(
            durationMs = metrics.elapsedMs,
            reps = metrics.repCount,
            hops = metrics.hops,
            crossoverCount = metrics.crossoverCount,
            lowDribbles = metrics.poundLow,
            hipDribbles = metrics.poundHip,
            highDribbles = metrics.poundHigh
        )
    }

    private fun evaluateDefensiveStance(frame: PoseFrame): String? {
        val leftAngle = hipKneeAngle(frame, left = true)
        val rightAngle = hipKneeAngle(frame, left = false)
        val average = listOfNotNull(leftAngle, rightAngle).average().toFloat()
        return if (average > Defaults.hipKneeAngleThresholdDeg) "Stay Low" else null
    }

    private fun evaluateLateralSlides(frame: PoseFrame): Float {
        val leftHip = frame.points[PoseLandmark.LEFT_HIP] ?: return metrics.lateralDistancePx
        val rightHip = frame.points[PoseLandmark.RIGHT_HIP] ?: return metrics.lateralDistancePx
        val midHipX = (leftHip.x + rightHip.x) / 2f
        val midHipY = (leftHip.y + rightHip.y) / 2f

        val yReference = previousHipY ?: midHipY
        val yTolerance = frame.height * Defaults.lateralYToleranceRatio

        val distance = if (abs(midHipY - yReference) <= yTolerance) {
            val previous = previousMidHipX ?: midHipX
            metrics.lateralDistancePx + abs(midHipX - previous)
        } else {
            metrics.lateralDistancePx
        }

        previousMidHipX = midHipX
        previousHipY = midHipY
        return distance
    }

    private fun evaluateJumpCadence(frame: PoseFrame): Int {
        val leftAnkle = frame.points[PoseLandmark.LEFT_ANKLE]
        val rightAnkle = frame.points[PoseLandmark.RIGHT_ANKLE]
        val hip = frame.points[PoseLandmark.LEFT_HIP]

        val y = when {
            leftAnkle != null && rightAnkle != null -> (leftAnkle.y + rightAnkle.y) / 2f
            hip != null -> hip.y
            else -> return 0
        }

        val previousY = previousHipY ?: y
        val dt = (frame.timestampMs - previousTimestamp).coerceAtLeast(1)
        val velocity = Geometry.velocity(previousY - y, dt)
        previousTimestamp = frame.timestampMs

        val crossingThreshold =
            velocity > Defaults.hopVelocityThresholdPxPerSec && prevVerticalVelocity <= Defaults.hopVelocityThresholdPxPerSec
        prevVerticalVelocity = velocity
        return if (crossingThreshold) 1 else 0
    }

    private fun evaluatePoundDribbleZone(frame: PoseFrame, ball: BallObservation?): DribbleZone {
        ball ?: return DribbleZone.NONE
        if (ball.confidence < Defaults.minHitConfidence) return DribbleZone.NONE

        val leftKnee = frame.points[PoseLandmark.LEFT_KNEE]?.y
        val rightKnee = frame.points[PoseLandmark.RIGHT_KNEE]?.y
        val leftHip = frame.points[PoseLandmark.LEFT_HIP]?.y
        val rightHip = frame.points[PoseLandmark.RIGHT_HIP]?.y
        val shoulder = averageY(frame, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)

        val kneeY = meanOf(leftKnee, rightKnee)
        val hipY = meanOf(leftHip, rightHip)
        val waistY = meanOf(kneeY, hipY)

        return when {
            kneeY != null && ball.centerY > kneeY -> DribbleZone.LOW
            waistY != null && shoulder != null && ball.centerY in shoulder..waistY -> DribbleZone.HIP
            shoulder != null && ball.centerY <= shoulder -> DribbleZone.HIGH
            else -> DribbleZone.NONE
        }
    }

    private fun evaluateCrossover(frame: PoseFrame, ball: BallObservation?): Int {
        ball ?: return 0
        val leftWrist = frame.points[PoseLandmark.LEFT_WRIST]
        val rightWrist = frame.points[PoseLandmark.RIGHT_WRIST]
        val bodyMidLineX = averageX(frame, PoseLandmark.NOSE, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return 0

        val currentSide = if (ball.centerX < bodyMidLineX) Side.LEFT else Side.RIGHT

        val hitRadius = 90f
        handStrikeDetected = (leftWrist != null && Geometry.distance(leftWrist.x, leftWrist.y, ball.centerX, ball.centerY) < hitRadius) ||
            (rightWrist != null && Geometry.distance(rightWrist.x, rightWrist.y, ball.centerX, ball.centerY) < hitRadius)

        val crossover = handStrikeDetected &&
            previousBallSide != Side.UNKNOWN &&
            currentSide != previousBallSide

        previousBallSide = currentSide
        return if (crossover) 1 else 0
    }

    private fun hipKneeAngle(frame: PoseFrame, left: Boolean): Float? {
        val hipType = if (left) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
        val kneeType = if (left) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
        val ankleType = if (left) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE

        val hip = frame.points[hipType] ?: return null
        val knee = frame.points[kneeType] ?: return null
        val ankle = frame.points[ankleType] ?: return null

        return Geometry.angleByLawOfCosines(
            ax = hip.x,
            ay = hip.y,
            bx = knee.x,
            by = knee.y,
            cx = ankle.x,
            cy = ankle.y
        )
    }

    private fun isUserInFrame(frame: PoseFrame): Boolean {
        val required = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE
        )
        return required.all { frame.points[it] != null }
    }

    private fun averageX(frame: PoseFrame, vararg types: Int): Float? {
        val values = types.mapNotNull { frame.points[it]?.x }
        return if (values.isEmpty()) null else values.average().toFloat()
    }

    private fun averageY(frame: PoseFrame, first: Int, second: Int): Float? {
        val one = frame.points[first]?.y ?: return null
        val two = frame.points[second]?.y ?: return null
        return (one + two) / 2f
    }

    private fun meanOf(a: Float?, b: Float?): Float? {
        if (a == null || b == null) return null
        return (a + b) / 2f
    }

    private fun resetLiveMetrics() {
        metrics = metrics.copy(stanceCue = null, debugText = "Waiting for full body in frame")
        previousMidHipX = null
        previousHipY = null
        prevVerticalVelocity = 0f
        previousTimestamp = 0L
    }

    private enum class Side { LEFT, RIGHT, UNKNOWN }

    private enum class DribbleZone { LOW, HIP, HIGH, NONE }
}
