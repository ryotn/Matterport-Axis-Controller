package jp.ryotn.panorama360

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraManager(context: Context) {
    private val TAG = "CameraManager"

    private val CONTEXT = context

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var focusDistance = CONTEXT.resources.getString(R.string.default_focus_distance).toFloat()

    fun startCamera(viewFinder: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(CONTEXT)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val previewBuilder = Preview.Builder()
            setFocusDistance(previewBuilder, focusDistance)
            preview = previewBuilder.build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                cameraProvider?.unbindAll()

                camera = cameraProvider?.bindToLifecycle(
                    CONTEXT as LifecycleOwner, cameraSelector, preview,imageCapture)
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(CONTEXT))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setFocusDistance(builder: ExtendableBuilder<*>?, distance: Float) {
        val extender: Camera2Interop.Extender<*> = Camera2Interop.Extender(builder as ExtendableBuilder<*>)
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CameraMetadata.CONTROL_AF_MODE_OFF
        )
        extender.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
    }

    fun resetFocusDistance(distance: Float, viewFinder: PreviewView) {
        stopCamera()
        focusDistance = distance
        startCamera(viewFinder)
    }
}