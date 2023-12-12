package jp.ryotn.panorama360

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraManager(context: Context) {
    private val TAG = "CameraManager"

    private val CONTEXT = context

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    fun startCamera(viewFinder: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(CONTEXT)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    CONTEXT as LifecycleOwner, cameraSelector, preview,imageCapture)
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(CONTEXT))
    }
}