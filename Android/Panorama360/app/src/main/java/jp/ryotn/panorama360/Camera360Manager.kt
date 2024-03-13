package jp.ryotn.panorama360

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
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
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
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
    companion object {
        private const val TAG = "Camera360Manager"
    }

    private val mContext = context
    private val mCameraManager: CameraManager by lazy {
        mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPhysicalCameraId: String? = null

    private var mCameraProvider: ProcessCameraProvider? = null
    private var mPreview: Preview? = null
    private var mImageCapture: ImageCapture? = null
    private var mCamera: Camera? = null
    private var mFocusDistance = mContext.resources.getString(R.string.default_focus_distance).toFloat()
    private var mFileCount = 0
    private val mDateFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
    private var mDocumentFile: DocumentFile? = null
    private var mSaveDocumentFile: DocumentFile? = null
    private var mSelectCameraSelector: CameraSelector? = null
    private var mExtensionsManager: ExtensionsManager? =null

    var mListener: Camera360ManagerListener? = null

    interface Camera360ManagerListener {

        fun initFinish()
        fun takePhotoSuccess()
        fun takePhotoError()
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
        cameraProviderFuture.addListener({
            mCameraProvider = cameraProviderFuture.get()
            mCameraProvider?.let {
                CameraInfoService.initService(it.availableCameraInfos, mContext)
                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(mContext, it)
                extensionsManagerFuture.addListener({
                    mExtensionsManager = extensionsManagerFuture.get()
                    mListener?.initFinish()
                }, ContextCompat.getMainExecutor(mContext))
            }
        }, ContextCompat.getMainExecutor(mContext))
    }

    @SuppressLint("MissingPermission")
    fun startCamera(viewFinder: TextureView, cameraId: String, physicalCameraId: String, mode:Int? = null) {
        cameraId.let { id ->
            mCameraManager.openCamera(id, object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    mCameraDevice = camera
                    createCameraPreviewSession(viewFinder, physicalCameraId)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    mCameraDevice?.close()
                    mCameraDevice = null
                }

                override fun onError(camera: CameraDevice, p1: Int) {
                    mCameraDevice?.close()
                    mCameraDevice = null
                }
            }, null)
        }
    }

    fun stopCamera() {
        mCaptureSession?.stopRepeating()
        mCameraDevice?.close()
        mCameraDevice = null
    }

    private fun createCameraPreviewSession(textureView: TextureView, physicalCameraId: String) {
        if (mCameraDevice == null) {
            return
        }
        mCameraDevice?.let { cameraDevice ->
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1024, 1024)
            val surface = Surface(texture)

            mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder?.addTarget(surface)

            mPhysicalCameraId = physicalCameraId

            val configurations: MutableList<OutputConfiguration> = ArrayList()
            val config = OutputConfiguration(surface)
            config.setPhysicalCameraId(mPhysicalCameraId)
            configurations.add(config)

            cameraDevice.createCaptureSessionByOutputConfigurations(configurations, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mPreviewRequestBuilder?.let {
                        mCaptureSession = session
                        mCaptureSession?.setRepeatingRequest(it.build(), null, null)
                        setFocusDistance(mFocusDistance)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        }
    }

    private fun getCameraCharacteristic(cameraId: String): CameraCharacteristics {
        return mCameraManager.getCameraCharacteristics(cameraId)
    }

    private fun getFocalLengthIn35mm(): Float {
        mPhysicalCameraId?.let { id ->
            val cameraCharacteristics = getCameraCharacteristic(id)
            val sensorWidth = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0.0F
            val focalLength = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.get(0) ?: 0.0F

            return (36 * focalLength) / sensorWidth
        }
        return 0.0F
    }

    private fun getFocusDistanceCalibration(): Int {
        mPhysicalCameraId?.let { id ->
            val cameraCharacteristics = getCameraCharacteristic(id)
            return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?:0
        }
        return 0
    }

    private fun getMinimumFocusDistance(): Float {
        mPhysicalCameraId?.let { id ->
            val cameraCharacteristics = getCameraCharacteristic(id)
            return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?:0.0F
        }
        return 0.0F
    }

    fun setFocusDistance(distance: Float) {
        mFocusDistance = distance
        mPreviewRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance)
        mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        mPreviewRequestBuilder?.let {
            mCaptureSession?.setRepeatingRequest(it.build(), null, null)
        }
    }

    fun takePhoto() {
        val imageCapture = mImageCapture ?: return
        val saveDocumentFile = mSaveDocumentFile ?: return
        if (!saveDocumentFile.exists()) {
            createDir()
            takePhoto()
            return
        }
        val createFile = saveDocumentFile.createFile("image/jpeg", "$mFileCount.jpg")
        val outputStream = createFile?.uri?.let { mContext.contentResolver.openOutputStream(it) }
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
        mDocumentFile = DocumentFile.fromTreeUri(mContext, path)
        createDir()
    }

    fun createDir() {
        val documentFile = mDocumentFile ?: return
        val now = LocalDateTime.now()
        val saveDirName = now.format(mDateFormatter)
        mSaveDocumentFile = documentFile.createDirectory(saveDirName)
        mFileCount = 0
    }

    //元コード
    //https://stackoverflow.com/questions/46442700/writing-exif-data-to-image-saved-with-documentfile-class
    private fun writeEXIFWithFileDescriptor(exifs: Array<Exif>, uri: Uri) {
        if (exifs.isEmpty()) return
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            parcelFileDescriptor = mContext.contentResolver.openFileDescriptor(uri, "rw")
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
            Log.e(TAG, "IOException " + e.message)
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