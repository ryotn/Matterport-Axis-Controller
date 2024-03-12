package jp.ryotn.panorama360

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Exif(val tag: String, val value: String) {
    override fun toString(): String {
        return "Exif tag:$tag value:$value"
    }
}

@OptIn(ExperimentalCamera2Interop::class)
class Camera360Manager(context: Context) {
    private val TAG = "Camera360Manager"

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
    private var mSelectCameraInfo: CameraInfo? = null
    private var mExtensionsManager: ExtensionsManager? =null

    var mListener: Camera360ManagerListener? = null

    interface Camera360ManagerListener {

        fun initFinish()
        fun takePhotoSuccess()
        fun takePhotoError()
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(CONTEXT)
        cameraProviderFuture.addListener({
            mCameraProvider = cameraProviderFuture.get()
            mCameraProvider?.let {
                CameraInfoService.initService(it.availableCameraInfos, CONTEXT)
                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(CONTEXT, it)
                extensionsManagerFuture.addListener({
                    mExtensionsManager = extensionsManagerFuture.get()
                    mListener?.initFinish()
                }, ContextCompat.getMainExecutor(CONTEXT))
            }
        }, ContextCompat.getMainExecutor(CONTEXT))
    }

    fun startCamera(viewFinder: PreviewView, cameraInfo: CameraInfo? = null, mode:Int? = null) {
        cameraInfo?.let {
            mSelectCameraInfo = it
        }
        val previewBuilder = Preview.Builder()
        preview = previewBuilder.build()

        imageCapture = ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        mSelectCameraInfo?.cameraSelector?.let {cameraSelector ->
            try {
                var selector = cameraSelector
                mode?.let { mode ->
                    mExtensionsManager?.let { extensionsManager ->
                        selector = extensionsManager.getExtensionEnabledCameraSelector(
                            cameraSelector,
                            mode
                        )
                    }
                }

                mCameraProvider?.unbindAll()

                camera = mCameraProvider?.bindToLifecycle(
                    CONTEXT as LifecycleOwner, selector, preview,imageCapture)
                setFocusDistance(focusDistance)
                val fdc = getFocusDistanceCalibration()
                val mfd = getMinimumFocusDistance()
                Log.d(TAG, "FocusDistanceCalibration: $fdc MinimumFocusDistance: $mfd")
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }
    }

    fun stopCamera() {
        mCameraProvider?.unbindAll()
    }

    private fun getCameraInfo(): Camera2CameraInfo? {
        mCameraProvider?.let { cameraProvider ->
            return mSelectCameraInfo?.cameraSelector?.filter(cameraProvider.availableCameraInfos)
                ?.firstOrNull()
                ?.let {
                Camera2CameraInfo.from(it)
            }
        }

        return null
    }

    private fun getFocalLengthIn35mm(): Float {
        val cameraInfo = getCameraInfo()
        cameraInfo?.let {
            val sensorWidth =
                cameraInfo.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width
                    ?: 0.0F
            val focalLength =
                cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.get(0) ?: 0.0F

            return (36 * focalLength) / sensorWidth
        }

        return 0.0F
    }

    private fun getFocusDistanceCalibration(): Int {
        val cameraInfo = getCameraInfo()
        cameraInfo?.let {
            return cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?:0
        }
        return 0
    }

    private fun getMinimumFocusDistance(): Float {
        val cameraInfo = getCameraInfo()
        cameraInfo?.let {
            return cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?:0.0F
        }
        return 0.0F
    }


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
                it, Dispatchers.Default.asExecutor(), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        mListener?.takePhotoError()
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        val focalLengthIn35mm = getFocalLengthIn35mm()
                        val exifs = arrayOf(Exif(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, focalLengthIn35mm.toInt().toString()),
                            Exif(ExifInterface.TAG_USER_COMMENT, "focalLengthIn35mm:$focalLengthIn35mm")
                        )
                        writeEXIFWithFileDescriptor(exifs, createFile.uri)
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

    //元コード
    //https://stackoverflow.com/questions/46442700/writing-exif-data-to-image-saved-with-documentfile-class
    private fun writeEXIFWithFileDescriptor(exifs: Array<Exif>, uri: Uri) {
        if (exifs.isEmpty()) return
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            parcelFileDescriptor = CONTEXT.contentResolver.openFileDescriptor(uri, "rw")
            parcelFileDescriptor?.fileDescriptor?.let {
                val exifInterface = ExifInterface(it)
                exifs.forEach { exif ->
                    exifInterface.setAttribute(exif.tag, exif.value)
                }
                exifInterface.saveAttributes()
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File Not Found " + e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "IOEXception " + e.message)
        } finally {
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close()
                } catch (ignored: IOException) {
                    ignored.printStackTrace()
                }
            }
        }
    }
}