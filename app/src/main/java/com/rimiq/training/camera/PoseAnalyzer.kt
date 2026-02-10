package com.rimiq.training.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.rimiq.training.model.LandmarkPoint
import com.rimiq.training.model.PoseFrame
import java.util.concurrent.atomic.AtomicBoolean

class PoseAnalyzer(
    private val isFrontCamera: Boolean,
    private val onPoseFrame: (PoseFrame) -> Unit,
    private val onFailure: (Throwable) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        .build()

    private val detector = PoseDetection.getClient(options)
    private val isProcessing = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                onPoseFrame(
                    PoseFrame(
                        timestampMs = System.currentTimeMillis(),
                        width = imageProxy.width,
                        height = imageProxy.height,
                        rotationDegrees = rotation,
                        isFrontCamera = isFrontCamera,
                        pose = pose,
                        points = extractLandmarks(pose)
                    )
                )
            }
            .addOnFailureListener(onFailure)
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    fun stop() {
        detector.close()
    }

    private fun extractLandmarks(pose: Pose): Map<Int, LandmarkPoint> {
        return pose.allPoseLandmarks.associate { landmark ->
            landmark.landmarkType to LandmarkPoint(
                x = landmark.position3D.x,
                y = landmark.position3D.y,
                z = landmark.position3D.z,
                confidence = landmark.inFrameLikelihood
            )
        }
    }
}
