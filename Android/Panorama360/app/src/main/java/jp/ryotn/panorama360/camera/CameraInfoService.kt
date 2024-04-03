package jp.ryotn.panorama360.camera
//元コード
//https://gist.github.com/mtkw0127/a905563f2b0c6cd0c610864a73c00ea6#file-camerainfoservice-kt
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.camera.core.CameraInfo

object CameraInfoService {
    // フロント
    private const val KEY_FRONT = "KEY_FRONT"

    // 望遠
    private const val KEY_TELEPHOTO = "KEY_TELEPHOTO"

    // 広角
    private const val KEY_WIDE_RANGE = "KEY_WIDE_RANGE"

    // 超広角
    private const val KEY_SUPER_WIDE_RANGE = "KEY_SUPER_WIDE_RANGE"

    private var mSortedCameraInfoMap: MutableMap<String, ExtendedCameraInfo>? = null

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    @SuppressLint("RestrictedApi")
    fun initService(cameraInfoList: List<CameraInfo>, context: Context) {
        if (mSortedCameraInfoMap != null) return
        mSortedCameraInfoMap = mutableMapOf()

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        // 焦点距離とそれに対するCameraInfoを取得する
        val extendedCameraInfoList = mutableListOf<ExtendedCameraInfo>()
        cameraIds.forEach { cameraId ->
            val characteristics =
                cameraManager.getCameraCharacteristics(cameraId)

            //フロントカメラはいらないのでreturn
            val isFront = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            if (isFront) return@forEach

            val isHDR = isExtensionSupported(cameraManager, cameraId, CameraExtensionCharacteristics.EXTENSION_HDR)
            val isNightMode = isExtensionSupported(cameraManager, cameraId, CameraExtensionCharacteristics.EXTENSION_NIGHT)

            val ids = characteristics.physicalCameraIds

            // physicalCameraIdsが空の端末もある場合はcameraIdのみ使う
            if (ids.isEmpty()) {
                val focalLength =
                    characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.getOrNull(0) ?: 0F

                val notAdded = extendedCameraInfoList.none { it.focalLength == focalLength }

                if (notAdded) {
                    extendedCameraInfoList.add(
                        ExtendedCameraInfo(cameraId, null, focalLength, characteristics, isHDR, isNightMode)
                    )
                }
            }

            ids.forEach idLoop@ { id ->
                // physicalCameraIdsがほかのcameraIdと被っている端末がある
                // その場合はスキップする
                if (cameraIds.contains(id)) return@idLoop
                val physicalCharacteristics = cameraManager.getCameraCharacteristics(id)

                // 焦点距離
                val focalLength =
                    physicalCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.getOrNull(0) ?: 0F

                val notAdded = extendedCameraInfoList.none { it.focalLength == focalLength }

                if (notAdded) {
                    extendedCameraInfoList.add(
                        ExtendedCameraInfo(cameraId, id, focalLength, physicalCharacteristics, isHDR, isNightMode)
                    )
                }
            }

        }

        // 焦点距離を考慮して広角、超広角、望遠のCameraを確定
        val camera0FocalLength = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.getOrNull(0) ?: 0F
        val sortedExtendedCameraInfoList = extendedCameraInfoList.sortedBy { it.focalLength }
        val wideRangeIndex = sortedExtendedCameraInfoList.indexOfFirst {it.focalLength == camera0FocalLength}//{ it.cameraId == "0" }

        // 超広角格納
        if (wideRangeIndex - 1 >= 0) {
            mSortedCameraInfoMap?.set(
                KEY_SUPER_WIDE_RANGE,
                sortedExtendedCameraInfoList[wideRangeIndex - 1]
            )
        }

        // 広角格納
        if (wideRangeIndex >= 0) {
            mSortedCameraInfoMap?.set(
                KEY_WIDE_RANGE,
                sortedExtendedCameraInfoList[wideRangeIndex]
            )
        }

        // 望遠角格納
        if (wideRangeIndex + 1 <= sortedExtendedCameraInfoList.lastIndex) {
            mSortedCameraInfoMap?.set(
                KEY_TELEPHOTO,
                sortedExtendedCameraInfoList[wideRangeIndex + 1]
            )
        }

        Log.d("CameraInfo","mSortedCameraInfoMap $mSortedCameraInfoMap")
    }

    data class ExtendedCameraInfo(
        val cameraId: String,
        val physicalCameraId: String?,
        val focalLength: Float,
        val cameraCharacteristics: CameraCharacteristics,
        val isHDR: Boolean,
        val isNightMode: Boolean,
    )

    //fun getTelephotoCameraInfo() = mSortedCameraInfoMap?.let { it[KEY_TELEPHOTO] }

    fun getWideRangeCameraInfo() = mSortedCameraInfoMap?.let { it[KEY_WIDE_RANGE] }

    fun getSuperWideRangeCameraInfo() = mSortedCameraInfoMap?.let { it[KEY_SUPER_WIDE_RANGE] }

    // 元コード
    // https://zenn.dev/watabee/scraps/af68e771f8baf1
    private fun isExtensionSupported(manager: CameraManager, id: String, extension: Int): Boolean {
        return manager
            .getCameraExtensionCharacteristics(id)
            .supportedExtensions.contains(extension)
    }
}