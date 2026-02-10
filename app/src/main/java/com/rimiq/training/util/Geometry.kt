package com.rimiq.training.util

import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

object Geometry {
    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return sqrt((ax - bx).pow(2) + (ay - by).pow(2))
    }

    // Angle at B for triangle A-B-C using law of cosines.
    fun angleByLawOfCosines(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float
    ): Float {
        val a = distance(bx, by, cx, cy)
        val b = distance(bx, by, ax, ay)
        val c = distance(ax, ay, cx, cy)
        val denominator = (2f * a * b).coerceAtLeast(0.0001f)
        val cosTheta = ((a * a + b * b - c * c) / denominator).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta).toDouble()).toFloat()
    }

    fun velocity(delta: Float, dtMs: Long): Float {
        if (dtMs <= 0L) return 0f
        return delta / (dtMs / 1000f)
    }
}
