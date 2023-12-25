package jp.ryotn.panorama360

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CameraManager(context: Context) {
    private val TAG = "CameraManager"

    private val CONTEXT = context

    private var mCameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var focusDistance = CONTEXT.resources.getString(R.string.default_focus_distance).toFloat()
    private var mFileCount = 0
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
    private var mDocumentFile: DocumentFile? = null
    private var mSaveDocumentFile: DocumentFile? = null

    var mListener: CameraManagerListener? = null

    interface CameraManagerListener {
        fun takePhotoSuccess()
        fun takePhotoError()
    }

    fun startCamera(viewFinder: PreviewView) {
        val mCameraProviderFuture = ProcessCameraProvider.getInstance(CONTEXT)

        mCameraProviderFuture.addListener({
            mCameraProvider = mCameraProviderFuture.get()

            val previewBuilder = Preview.Builder()
            preview = previewBuilder.build()

            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                mCameraProvider?.unbindAll()

                camera = mCameraProvider?.bindToLifecycle(
                    CONTEXT as LifecycleOwner, cameraSelector, preview,imageCapture)
                setFocusDistance(focusDistance)
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(CONTEXT))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getFocalLengthIn35mm(): Float {
        mCameraProvider?.let { cameraProvider ->
            val cameraInfo = CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraProvider.availableCameraInfos).firstOrNull()?.let {
                Camera2CameraInfo.from(it)
            }
            cameraInfo?.let {
                val sensorWidth =
                    cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width
                        ?: 0.0F
                val focalLength =
                    cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.get(0) ?: 0.0F

                return (36 * focalLength) / sensorWidth
            }
        }

        return  0.0F
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setFocusDistance(distance: Float) {
        focusDistance = distance
        camera?.cameraControl?.let {
            val camera2CameraControl : Camera2CameraControl = Camera2CameraControl.from(it)
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                .build()
            camera2CameraControl.captureRequestOptions = captureRequestOptions
        }
    }

    fun takePhoto() {
        if (mFileCount == 0) writeTextFileFocalLengthIn35mm()
        val imageCapture = imageCapture ?: return
        val saveDocumentFile = mSaveDocumentFile ?: return
        if (!saveDocumentFile.exists()) {
            createDir()
            takePhoto()
            return
        }
        val createFile = saveDocumentFile.createFile("image/jpeg", "$mFileCount.jpg")
        val outputStream = createFile?.uri?.let { CONTEXT.contentResolver.openOutputStream(it) }
        val outputOptions = outputStream?.let { ImageCapture.OutputFileOptions.Builder(it).build() }

        outputOptions?.let {
            imageCapture.takePicture(
                it, ContextCompat.getMainExecutor(CONTEXT), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        mListener?.takePhotoError()
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        mListener?.takePhotoSuccess()
                        Log.d(TAG, msg)
                        mFileCount++
                    }
                })
        }
    }

    fun setOutputDirectory(path: Uri) {
        mDocumentFile = DocumentFile.fromTreeUri(CONTEXT, path)
        createDir()
    }

    fun createDir() {
        val documentFile = mDocumentFile ?: return
        val now = LocalDateTime.now()
        val saveDirName = now.format(dateFormatter)
        mSaveDocumentFile = documentFile.createDirectory(saveDirName)
        mFileCount = 0
    }

    fun writeTextFileFocalLengthIn35mm() {
        mSaveDocumentFile?.createFile("text/plain", "focalLengthIn35mm.txt")?.let {
            val outputStream = CONTEXT.contentResolver.openOutputStream(it.uri)
            outputStream?.write(getFocalLengthIn35mm().toString().toByteArray())
            outputStream?.flush()
            outputStream?.close()
        }
    }
}