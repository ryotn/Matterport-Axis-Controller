package jp.ryotn.panorama360
//元コード
//https://gist.github.com/mtkw0127/a905563f2b0c6cd0c610864a73c00ea6#file-camerainfoservice-kt
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import androidx.camera.camera2.interop.Camera2CameraInfo
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

    private var sortedCameraInfoMap: MutableMap<String, ExtendedCameraInfo>? = null

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    @SuppressLint("RestrictedApi")
    fun initService(cameraInfoList: List<CameraInfo>, context: Context) {
        if (sortedCameraInfoMap != null) return
        sortedCameraInfoMap = mutableMapOf()

        // 焦点距離とそれに対するCameraInfoを取得する
        val extendedCameraInfoList = mutableListOf<ExtendedCameraInfo>()
        cameraInfoList.forEach { cameraInfo ->
            val cameraManager = CameraManagerCompat.from(context).unwrap()
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            val characteristics =
                cameraManager.getCameraCharacteristics(camera2Info.cameraId)
            // カメラID
            val cameraId = camera2Info.cameraId

            // 背面レンズ
            val isBack =
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK

            // 焦点距離
            val focalLength =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.getOrNull(0) ?: 0F

            if (isBack.not()) {
                // フロントカメラ
                sortedCameraInfoMap?.set(
                    KEY_FRONT,
                    ExtendedCameraInfo(cameraId, focalLength, cameraInfo, characteristics)
                )
                return@forEach
            }

            val notAdded = extendedCameraInfoList.none { it.focalLength == focalLength }

            if (notAdded) {
                extendedCameraInfoList.add(
                    ExtendedCameraInfo(cameraId, focalLength, cameraInfo, characteristics)
                )
            }
        }

        // 焦点距離を考慮して広角、超広角、望遠のCameraを確定
        val sortedExtendedCameraInfoList = extendedCameraInfoList.sortedBy { it.focalLength }
        val wideRangeIndex = sortedExtendedCameraInfoList.indexOfFirst { it.cameraId == "0" }

        // 超広角格納
        if (wideRangeIndex - 1 >= 0) {
            sortedCameraInfoMap?.set(
                KEY_SUPER_WIDE_RANGE,
                sortedExtendedCameraInfoList[wideRangeIndex - 1]
            )
        }

        // 広角格納
        if (wideRangeIndex >= 0) {
            sortedCameraInfoMap?.set(
                KEY_WIDE_RANGE,
                sortedExtendedCameraInfoList[wideRangeIndex]
            )
        }

        // 望遠角格納
        if (wideRangeIndex + 1 <= sortedExtendedCameraInfoList.lastIndex) {
            sortedCameraInfoMap?.set(
                KEY_TELEPHOTO,
                sortedExtendedCameraInfoList[wideRangeIndex + 1]
            )
        }
    }

    data class ExtendedCameraInfo(
        val cameraId: String,
        val focalLength: Float,
        val cameraInfo: CameraInfo,
        val cameraCharacteristics: CameraCharacteristics,
    )

    fun getTelephotoCameraInfo() = sortedCameraInfoMap?.let { it[KEY_TELEPHOTO] }

    fun getWideRangeCameraInfo() = sortedCameraInfoMap?.let { it[KEY_WIDE_RANGE] }

    fun getSuperWideRangeCameraInfo() = sortedCameraInfoMap?.let { it[KEY_SUPER_WIDE_RANGE] }
}