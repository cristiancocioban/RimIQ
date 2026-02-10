package com.rimiq.training.camera

import androidx.camera.core.ImageProxy
import com.rimiq.training.model.BallObservation

/**
 * MVP stub for ball tracking.
 * In production you can replace with a TFLite detector (YOLO/SSD) and keep this contract.
 */
interface BallTracker {
    fun detect(imageProxy: ImageProxy): BallObservation?
}

class NullBallTracker : BallTracker {
    override fun detect(imageProxy: ImageProxy): BallObservation? = null
}
