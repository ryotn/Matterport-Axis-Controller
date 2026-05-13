package jp.ryotn.panorama360.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import jp.ryotn.panorama360.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.sqrt


data class Exif(val tag: String, val value: String) {
    override fun toString(): String {
        return "Exif tag:$tag value:$value"
    }
}

@OptIn(ExperimentalCamera2Interop::class)
class Camera360Manager(context: Context) {
    companion object {
        private const val TAG = "Camera360Manager"
        private const val IMAGE_BUFFER_SIZE = 7
        const val DEFAULT_EDGE_DETECTION_THRESHOLD = 400
        private const val FOCUS_PEAKING_RED = 255
        private const val FOCUS_PEAKING_GREEN = 0
        private const val FOCUS_PEAKING_BLUE = 0
        const val FOCUS_PEAKING_THRESHOLD_MIN = 0f
        const val FOCUS_PEAKING_THRESHOLD_MAX = 150f
        const val FOCUS_PEAKING_THRESHOLD_OFFSET = 400
        val EXPOSURE_BRACKET_LIST = arrayOf(
            intArrayOf(0),
            intArrayOf(0, -1, 1),
            intArrayOf(0, -2, -1, 1, 2),
            intArrayOf(0, -3, -2, -1, 1, 2, 3)
        )
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
    private var mFocusPeakingReader: ImageReader? = null
    private var mFocusPeakingThread: HandlerThread? = null
    private var mFocusPeakingHandler: Handler? = null
    private var mIsFocusPeakingEnabled = false
    private var mEdgeDetectionThreshold = DEFAULT_EDGE_DETECTION_THRESHOLD
    private var mEdgeDetectionThresholdSq = DEFAULT_EDGE_DETECTION_THRESHOLD * DEFAULT_EDGE_DETECTION_THRESHOLD
    private var mExposureBracketMode = 0
    private var mExposureBracketCount = 0

    private var mCameraProvider: ProcessCameraProvider? = null
    private var mFocusDistance = mContext.resources.getString(R.string.default_focus_distance).toFloat()
    private var mFileCount = 0
    private val mDateFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
    private var mDocumentFile: DocumentFile? = null
    private var mSaveDocumentFile: DocumentFile? = null
    private var mExtensionsManager: ExtensionsManager? =null
    private var mDeviceOrientation = ExifInterface.ORIENTATION_ROTATE_90

    var mListener: Camera360ManagerListener? = null
    var isStart = false

    interface Camera360ManagerListener {

        fun initFinish()
        fun startCameraConfigured(context: Context)
        fun takePhotoSuccess()
        fun takePhotoError()
        fun onFocusPeakingUpdate(bitmap: Bitmap?)
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
        cameraProviderFuture.addListener({
            mCameraProvider = cameraProviderFuture.get()
            mCameraProvider?.let {
                CameraInfoService.initService(mContext)
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
        mFocusPeakingReader?.close()
        mFocusPeakingReader = null
        mFocusPeakingHandler?.removeCallbacksAndMessages(null)
        mFocusPeakingThread?.quitSafely()
        mFocusPeakingThread = null
        mFocusPeakingHandler = null
        // Clear reused processing buffers only after the handler thread is stopped
        mFocusPeakingBitmap = null
        mFocusPeakingPixels = null
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
            var focusPeakingSize = Size(320, 240)
            getSupportSizes(cameraDevice.id, ImageFormat.YUV_420_888)?.let { sizes ->
                size = sizes.filter { s ->
                    s.width in 1025..2023 && (s.height.toFloat() / s.width.toFloat()) == 0.75F
                }[0]
                val fpSizes = sizes.filter { s ->
                    s.width <= 640 && (s.height.toFloat() / s.width.toFloat()) == 0.75F
                }
                focusPeakingSize = fpSizes.maxByOrNull { it.height * it.width } ?: focusPeakingSize
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
                imageReaderSize.width, imageReaderSize.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE
            )

            // Set up focus peaking ImageReader (YUV_420_888 at lower resolution)
            mFocusPeakingThread = HandlerThread("FocusPeakingThread").also { it.start() }
            mFocusPeakingHandler = Handler(mFocusPeakingThread!!.looper)
            mFocusPeakingReader = ImageReader.newInstance(
                focusPeakingSize.width, focusPeakingSize.height, ImageFormat.YUV_420_888, 2
            )
            mFocusPeakingReader?.setOnImageAvailableListener(mFocusPeakingImageListener, mFocusPeakingHandler)

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
                val focusPeakingConfig = OutputConfiguration(mFocusPeakingReader!!.surface)
                if (mPhysicalCameraId != null) focusPeakingConfig.setPhysicalCameraId(mPhysicalCameraId)
                configurations.add(focusPeakingConfig)

                mPreviewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                mPreviewRequestBuilder?.addTarget(mSurface!!)
                mPreviewRequestBuilder?.addTarget(mFocusPeakingReader!!.surface)

                cameraDevice.createCaptureSessionByOutputConfigurations(
                    configurations,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            mPreviewRequestBuilder?.let {
                                mPreviewSession = session
                                mPreviewSession?.setRepeatingRequest(it.build(), null, null)
                                setFocusDistance(mFocusDistance)
                            }
                            mListener?.startCameraConfigured(mContext)
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

            mListener?.startCameraConfigured(mContext)
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

    fun setExposureBracketMode(mode: Int) {
         mExposureBracketMode = mode
    }

    fun getFocalLengthIn35mm(): Float {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        val sensorWidth = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0.0F
        val focalLength = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.get(0) ?: 0.0F

        return (36 * focalLength) / sensorWidth
    }

    fun getAeCompensationRange(): Range<Int> {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?:Range(0,0)
    }

    fun getAeCompensationStep(): Double {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble() ?: 0.0
    }

    private fun getFocusDistanceCalibration(): Int {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?:0
    }

    private fun getMinimumFocusDistance(): Float {
        val cameraCharacteristics = getCameraCharacteristic(getCurrentCameraId())
        return cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?:0.0F
    }

    fun setOrientation(orientation: Int) {
        mDeviceOrientation = orientation
    }

    fun setFocusDistance(distance: Float) {
        val normalizedDistance = distance.coerceIn(0f, 1f)
        val minimumFocusDistance = getMinimumFocusDistance()
        mFocusDistance = if (minimumFocusDistance > 0f) {
            normalizedDistance * minimumFocusDistance
        } else {
            0f
        }
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

        val exposureStep = getAeCompensationStep()
        val exposureBracketList = EXPOSURE_BRACKET_LIST[mExposureBracketMode]
        val isExposureBracket = exposureBracketList.count() != 1

        val requestList: MutableList<CaptureRequest> = ArrayList()

        exposureBracketList.forEach { exposureBracket ->
            val captureRequestBuilder =
                mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    .apply {
                        this?.addTarget(mImageReader!!.surface)
                        this?.set(CaptureRequest.CONTROL_AE_LOCK, true)
                        this?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                            (exposureBracket.toDouble() / exposureStep).toInt()
                        )
                        this?.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance)
                        this?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    }
            captureRequestBuilder?.build()?.let { requestList.add(it) }
        }
        mImageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            when (image.format) {
                ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    var fileName = "$mFileCount.jpg"
                    if (isExposureBracket) {
                        fileName = "${mFileCount}_EV${exposureBracketList[mExposureBracketCount]}.jpg"
                    }

                    val createFile = saveDocumentFile.createFile("image/jpeg", fileName)
                    val outputStream = createFile?.uri?.let { mContext.contentResolver.openOutputStream(it) }
                    try {
                        outputStream?.write(bytes)
                        outputStream?.close()
                        image.close()
                        val focalLengthIn35mm = getFocalLengthIn35mm()
                        val exifs = arrayOf(
                            Exif(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, focalLengthIn35mm.toInt().toString()),
                            Exif(ExifInterface.TAG_USER_COMMENT, "focalLengthIn35mm:$focalLengthIn35mm"),
                            Exif(ExifInterface.TAG_ORIENTATION, mDeviceOrientation.toString()),
                            Exif(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "${exposureBracketList[mExposureBracketCount]}/1"),
                            Exif(ExifInterface.TAG_EXPOSURE_MODE, ExifInterface.EXPOSURE_MODE_AUTO_BRACKET.toString()),
                        )
                        writeEXIFWithFileDescriptor(exifs, createFile!!.uri)
                        val msg = "Photo capture succeeded: ${createFile.uri}"
                        Log.d(TAG, msg)
                        mExposureBracketCount++
                        if (mExposureBracketCount >= exposureBracketList.count()) {
                            mFileCount++
                            mExposureBracketCount = 0
                            mListener?.takePhotoSuccess()
                        }
                    } catch (exc: IOException) {
                        Log.e(TAG, "Unable to write JPEG image to file", exc)
                    }
                }
            }

            //mImageReader?.setOnImageAvailableListener(null, null)
        }, Handler(HandlerThread("CameraThread").apply { start() }.looper))

        mPreviewSession?.captureBurst(requestList, object : CameraCaptureSession.CaptureCallback(){
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

    fun setFocusPeaking(enable: Boolean) {
        mIsFocusPeakingEnabled = enable
        if (!enable) {
            mListener?.onFocusPeakingUpdate(null)
        }
    }

    fun setFocusPeakingThreshold(value: Int) {
        mEdgeDetectionThreshold = FOCUS_PEAKING_THRESHOLD_OFFSET + value
        mEdgeDetectionThresholdSq = mEdgeDetectionThreshold * mEdgeDetectionThreshold
    }

    // Reused across frames; accessed exclusively from mFocusPeakingHandler (single thread).
    // Cleared in stopCamera() after the handler thread is stopped to prevent stale references.
    private var mFocusPeakingBitmap: Bitmap? = null
    private var mFocusPeakingPixels: IntArray? = null

    private val mFocusPeakingImageListener = ImageReader.OnImageAvailableListener { reader ->
        // Always acquire and close the image to drain the ImageReader buffer and
        // prevent the camera capture pipeline from stalling.
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            if (!mIsFocusPeakingEnabled) {
                return@OnImageAvailableListener
            }

            val yPlane = image.planes[0]
            val width = image.width
            val height = image.height
            val stride = yPlane.rowStride
            val buffer = yPlane.buffer
            val yData = ByteArray(buffer.remaining())
            buffer.get(yData)

            val bitmap = applySobelEdgeDetection(yData, width, height, stride)
            mListener?.onFocusPeakingUpdate(bitmap)
        } finally {
            image.close()
        }
    }

    private fun applySobelEdgeDetection(yPlane: ByteArray, width: Int, height: Int, stride: Int): Bitmap {
        val pixels = mFocusPeakingPixels?.takeIf { it.size == width * height }
            ?: IntArray(width * height).also { mFocusPeakingPixels = it }
        val bitmap = mFocusPeakingBitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { mFocusPeakingBitmap = it }

        // Clear pixels from the previous frame. The Sobel loop only writes edge pixels
        // (magnitude > threshold), so non-edge positions must be explicitly zeroed to
        // avoid stale colour data from the prior frame persisting in the overlay.
        pixels.fill(0)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val p00 = yPlane[(y - 1) * stride + (x - 1)].toInt() and 0xFF
                val p01 = yPlane[(y - 1) * stride + x].toInt() and 0xFF
                val p02 = yPlane[(y - 1) * stride + (x + 1)].toInt() and 0xFF
                val p10 = yPlane[y * stride + (x - 1)].toInt() and 0xFF
                val p12 = yPlane[y * stride + (x + 1)].toInt() and 0xFF
                val p20 = yPlane[(y + 1) * stride + (x - 1)].toInt() and 0xFF
                val p21 = yPlane[(y + 1) * stride + x].toInt() and 0xFF
                val p22 = yPlane[(y + 1) * stride + (x + 1)].toInt() and 0xFF

                val gx = -p00 - 2 * p10 - p20 + p02 + 2 * p12 + p22
                val gy = -p00 - 2 * p01 - p02 + p20 + 2 * p21 + p22

                val magnitudeSq = gx * gx + gy * gy
                if (magnitudeSq > mEdgeDetectionThresholdSq) {
                    // sqrt is only called for the minority of pixels that exceed the threshold
                    val alpha = min(255, sqrt(magnitudeSq.toDouble()).toInt())
                    pixels[y * width + x] = Color.argb(alpha, FOCUS_PEAKING_RED, FOCUS_PEAKING_GREEN, FOCUS_PEAKING_BLUE)
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
