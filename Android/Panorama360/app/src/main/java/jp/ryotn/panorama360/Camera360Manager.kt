package jp.ryotn.panorama360

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraExtensionSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
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
        private const val IMAGE_BUFFER_SIZE = 3
    }

    private val mContext = context
    private val mCameraManager: CameraManager by lazy {
        mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var mCaptureExtensionSession: CameraExtensionSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPhysicalCameraId: String? = null
    private var mSurface: Surface? = null
    private var mImageReader: ImageReader? = null

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
    var isStart = false

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
    fun startCamera(viewFinder: TextureView, cameraId: String, physicalCameraId: String?, mode:Int? = null) {
        cameraId.let { id ->
            mCameraManager.openCamera(id, object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isStart = true
                    mCameraDevice = camera
                    createCameraPreviewSession(viewFinder, physicalCameraId, mode)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isStart = false
                    mCameraDevice?.close()
                    mCameraDevice = null
                }

                override fun onError(camera: CameraDevice, p1: Int) {
                    isStart = true
                    mCameraDevice?.close()
                    mCameraDevice = null
                }
            }, null)
        }
    }

    fun stopCamera() {
        isStart = false
        mPreviewSession?.close()
        mPreviewSession = null
        mCaptureExtensionSession?.close()
        mCaptureExtensionSession = null
        mCameraDevice?.close()
        mCameraDevice = null
        mPreviewRequestBuilder = null
    }

    private fun getExtensionSupportSizes(id: String, extension: Int, imageFormat: Int): List<Size> {
        return mCameraManager
            .getCameraExtensionCharacteristics(id)
            .getExtensionSupportedSizes(extension, imageFormat)
    }

    private fun getSupportSizes(id: String, imageFormat: Int): Array<Size>? {
        return mCameraManager
            .getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(imageFormat)
    }

    private fun createCameraPreviewSession(textureView: TextureView, physicalCameraId: String?, mode: Int?) {
        if (mCameraDevice == null) {
            return
        }
        mCameraDevice?.let { cameraDevice ->
            var size = Size(1024, 1024)
            var imageReaderSize = Size(1024, 1024)
            getSupportSizes(cameraDevice.id, ImageFormat.YUV_420_888)?.let { sizes ->
                size = sizes.filter { s ->
                    s.width in 1025..2023 && (s.height.toFloat() / s.width.toFloat()) == 0.75F
                }[0]
            }
            getSupportSizes(cameraDevice.id, ImageFormat.JPEG)?.let { sizes ->
                imageReaderSize = sizes.filter { s ->
                    (s.height.toFloat() / s.width.toFloat()) == 0.75F
                }.maxByOrNull { it.height * it.width }!!
            }
            mode?.let { mode ->
                val extensionsSizes = getExtensionSupportSizes(cameraDevice.id, mode, ImageFormat.YUV_420_888)
                size = extensionsSizes.filter { s ->
                    s.width in 1025..2023 && (s.height.toFloat() / s.width.toFloat()) == 0.75F
                }[0]
                val extensionsSizesReader = getExtensionSupportSizes(cameraDevice.id, mode, ImageFormat.JPEG)
                imageReaderSize = extensionsSizesReader.maxByOrNull { it.height * it.width }!!
            }
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(size.width, size.height)
            mSurface = Surface(texture)
            mImageReader = ImageReader.newInstance(
                imageReaderSize.width, imageReaderSize.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)

            mPhysicalCameraId = physicalCameraId

            val configurations: MutableList<OutputConfiguration> = ArrayList()
            val config = OutputConfiguration(mSurface!!)
            val imageReaderConfig = OutputConfiguration(mImageReader!!.surface)
            if (mPhysicalCameraId != null) config.setPhysicalCameraId(mPhysicalCameraId)
            if (mPhysicalCameraId != null) imageReaderConfig.setPhysicalCameraId(mPhysicalCameraId)
            configurations.add(config)
            configurations.add(imageReaderConfig)

            if (mode != null) {
                val extensionConfiguration = ExtensionSessionConfiguration(
                    mode,
                    configurations,
                    Dispatchers.IO.asExecutor(),
                    extensionSessionStateCallback
                )
                cameraDevice.createExtensionSession(extensionConfiguration)
            } else {

                mPreviewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                mPreviewRequestBuilder?.addTarget(mSurface!!)

                cameraDevice.createCaptureSessionByOutputConfigurations(
                    configurations,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mPreviewRequestBuilder?.let {
                                mPreviewSession = session
                                mPreviewSession?.setRepeatingRequest(it.build(), null, null)
                                setFocusDistance(mFocusDistance)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    null
                )
            }
        }
    }

    private val extensionSessionStateCallback = object : CameraExtensionSession.StateCallback() {
        override fun onConfigured(session: CameraExtensionSession) {
            try {
                mCameraDevice?.let { cameraDevice ->
                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(mSurface!!)
                        }
                    mPreviewRequestBuilder = captureRequest
                    session.setRepeatingRequest(
                        captureRequest.build(),
                        Dispatchers.IO.asExecutor(),
                        object : CameraExtensionSession.ExtensionCaptureCallback() {
                        }
                    )
                }

            } catch (e: CameraAccessException) {
                Log.d(TAG, "Failed to preview capture request $e")
            }
        }

        override fun onClosed(session: CameraExtensionSession) {
            super.onClosed(session)
            mCameraDevice?.close()
        }

        override fun onConfigureFailed(session: CameraExtensionSession) {
            mCameraDevice?.close()
        }
    }

    private fun getCameraCharacteristic(cameraId: String): CameraCharacteristics {
        return mCameraManager.getCameraCharacteristics(cameraId)
    }

    private fun getCurrentCameraId(): String {
        mPhysicalCameraId?.let {
            return it
        }

        mCameraDevice?.let {
            return it.id
        }

        return ""
    }

    private fun getFocalLengthIn35mm(): Float {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        val sensorWidth = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0.0F
        val focalLength = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.get(0) ?: 0.0F

        return (36 * focalLength) / sensorWidth
    }

    private fun getFocusDistanceCalibration(): Int {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?:0
    }

    private fun getMinimumFocusDistance(): Float {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?:0.0F
    }

    fun setFocusDistance(distance: Float) {
        mFocusDistance = distance
        mPreviewRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance)
        mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        mPreviewRequestBuilder?.let {
            mPreviewSession?.setRepeatingRequest(it.build(), null, null)
            mCaptureExtensionSession?.setRepeatingRequest(it.build(), Dispatchers.IO.asExecutor(),
                object : CameraExtensionSession.ExtensionCaptureCallback() {
                    // Implement Capture Callbacks
                })
        }
    }

    fun takePhoto() {
        val saveDocumentFile = mSaveDocumentFile ?: return
        if (!saveDocumentFile.exists()) {
            createDir()
            takePhoto()
            return
        }

        val createFile = saveDocumentFile.createFile("image/jpeg", "$mFileCount.jpg")
        val outputStream = createFile?.uri?.let { mContext.contentResolver.openOutputStream(it) }

        val captureRequestBuilder =
            mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                .apply {
                    this?.addTarget(mImageReader!!.surface)
                }
        mImageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            when (image.format) {
                ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    try {
                        outputStream?.write(bytes)
                        outputStream?.close()
                        val focalLengthIn35mm = getFocalLengthIn35mm()
                        val exifs = arrayOf(Exif(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, focalLengthIn35mm.toInt().toString()),
                            Exif(ExifInterface.TAG_USER_COMMENT, "focalLengthIn35mm:$focalLengthIn35mm"),
                            Exif(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                        )
                        writeEXIFWithFileDescriptor(exifs, createFile!!.uri)
                        image.close()
                        mListener?.takePhotoSuccess()
                        val msg = "Photo capture succeeded: ${createFile.uri}"
                        Log.d(TAG, msg)
                        mFileCount++
                    } catch (exc: IOException) {
                        Log.e(TAG, "Unable to write JPEG image to file", exc)
                    }
                }
            }

            mImageReader?.setOnImageAvailableListener(null, null)
        }, Handler(HandlerThread("CameraThread").apply { start() }.looper))

        if (captureRequestBuilder != null) {
            mPreviewSession?.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback(){
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    Log.d(TAG, "onCaptureStarted")
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d(TAG, "onCaptureCompleted")
                }
            }, Handler(HandlerThread("CameraThread").apply { start() }.looper))
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