package jp.ryotn.panorama360.model

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.view.TextureView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import jp.ryotn.panorama360.camera.Camera360Manager
import jp.ryotn.panorama360.camera.CameraInfoService
import jp.ryotn.panorama360.MatterportAxisManager
import jp.ryotn.panorama360.MotionManager
import jp.ryotn.panorama360.R
import jp.ryotn.panorama360.SoundPlayer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.round

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private lateinit var mDefaultPreference: SharedPreferences
    private var mCamera360Manager: Camera360Manager? = null
    private lateinit var mMatterportAxisManager: MatterportAxisManager
    private lateinit var mSoundPlayer: SoundPlayer
    private lateinit var mMotionManager: MotionManager

    val mFocus: MutableStateFlow<Float> = MutableStateFlow(0.4f) //プレビュー用のダミー
    var mExposureBracketModeList: MutableStateFlow<List<String>> = MutableStateFlow(listOf("")) //プレビュー用のダミー
    var mExposureBracketMode = 0
    val isConnect: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val mAngle: MutableStateFlow<Int> = MutableStateFlow(0)
    val isPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isUltraWide: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private var mSelectedCameraInfo: CameraInfoService.ExtendedCameraInfo? = null
    @SuppressLint("StaticFieldLeak")
    private var mViewFinder: TextureView? =null

    private var isShooting: Boolean = false
    private var mShotAngleSum: Int = 0
    private var mRotationAngle: Int = 30

    fun init(isPreview: Boolean = false) {
        if (!isPreview) {
            mFocus.value = application.getString(R.string.default_focus_distance).toFloat()
            mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(application)
            mMatterportAxisManager = MatterportAxisManager(context = application)
            mSoundPlayer = SoundPlayer(context = application)
            mMotionManager = MotionManager(context = application)

            mMatterportAxisManager.mListener = mMatterportAxisManagerListener
        }
    }

    fun onDestroy() {
        mCamera360Manager?.stopCamera()
        mMatterportAxisManager.disconnect()
    }

    fun initCamera360Manager() {
        if (mCamera360Manager?.isStart == true) return

        if (mCamera360Manager == null) {
            mCamera360Manager = Camera360Manager(context = application)
            mCamera360Manager?.mListener = mCamera360ManagerListener
        }

        if (isPermission.value) {
            CameraInfoService.getWideRangeCameraInfo()?.let {
                changeCamera(it)
            }
        }
    }

    fun setExposureBracketMode(mode: Int) {
        mExposureBracketMode = mode
        mCamera360Manager?.setExposureBracketMode(mode)
    }

    fun setFocus(f: Float) {
        mFocus.value = round(f * 10.0f) / 10.0f
        mCamera360Manager?.setFocusDistance(mFocus.value)
    }

    fun toggleCamera(): String {
        var nextCameraLabel = ""
        var nextCameraInfo: CameraInfoService.ExtendedCameraInfo? = null
        mSelectedCameraInfo?.let { cameraInfo ->
            nextCameraInfo = if (cameraInfo == CameraInfoService.getWideRangeCameraInfo()) {
                nextCameraLabel = application.getString(R.string.ultra_wide)
                mRotationAngle = 60
                CameraInfoService.getSuperWideRangeCameraInfo()
            } else {
                nextCameraLabel = application.getString(R.string.wide)
                mRotationAngle = 30
                CameraInfoService.getWideRangeCameraInfo()
            }
        }

        nextCameraInfo?.let { cameraInfo ->
            changeCamera(cameraInfo)
        }

        return nextCameraLabel
    }

    fun startCapture() {
        if (!isConnect.value) {
            mCamera360Manager?.takePhoto()
            return
        }

        if (isShooting) return
        mMotionManager.start()
        mSoundPlayer.playStartSound()
        mShotAngleSum = mRotationAngle
        Handler(Looper.getMainLooper()).postDelayed({
            mCamera360Manager?.takePhoto()
        }, 500)
        Handler(Looper.getMainLooper()).postDelayed({
            isShooting = true
        }, 1000)
    }

    fun createDir() {
        mCamera360Manager?.createDir()
    }

    private fun setFilePath() {
        val uriStr = mDefaultPreference.getString("uri", null)
        if (uriStr.isNullOrEmpty()) {
            showToast("保存先へのpermissionが取れてないよ")
        } else {
            val dir = DocumentFile.fromTreeUri(application, uriStr.toUri())
            dir?.let {
                if (it.canWrite()) {
                    Log.d(TAG, "保存先のPermission取得済み $uriStr")
                    mCamera360Manager?.setOutputDirectory(uriStr.toUri())
                } else {
                    showToast("保存先へのpermissionが取れてないよ")
                }
            }
        }
    }

    private fun changeCamera(extendedCameraInfo: CameraInfoService.ExtendedCameraInfo) {
        mSelectedCameraInfo = extendedCameraInfo
        mCamera360Manager?.stopCamera()
        mViewFinder?.let { mCamera360Manager?.startCamera(it,extendedCameraInfo.cameraId ,extendedCameraInfo.physicalCameraId) }
    }

    //Matterport
    fun connectMatterport() {
        if (mMatterportAxisManager.isConnected()) {
            mMatterportAxisManager.disconnect()
            showToast(application.getString(R.string.disconnecting))
        } else {
            mMatterportAxisManager.connect()
            showToast(application.getString(R.string.connecting))
        }
    }

    fun resetAngle() {
        mMatterportAxisManager.resetAngle()
    }

    fun putPreferenceString(key: String, value: String) {
        mDefaultPreference.edit {
            putString(key, value)
        }
    }

    fun isFilePermission(): Boolean {
        return !mDefaultPreference.getString("uri", null).isNullOrEmpty()
    }

    fun setViewFinder(textureView: TextureView) {
        mViewFinder = textureView
    }

    fun setPermission(permission: Boolean) {
        isPermission.value = permission
    }

    private fun showToast(text: String) {
        Toast.makeText(application, text, Toast.LENGTH_SHORT).show()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val mMatterportAxisManagerListener = object : MatterportAxisManager.MatterportAxisManagerListener {
        override fun connected() {
            GlobalScope.launch(Dispatchers.Main) {
                isConnect.value = true
                showToast(application.getString(R.string.connected))
            }
        }

        override fun disconnected() {
            GlobalScope.launch(Dispatchers.Main) {
                isConnect.value = false
            }
        }

        override fun receiveAngle() {
            GlobalScope.launch(Dispatchers.Main) {
                mAngle.value = mMatterportAxisManager.getAngle()
                if (isShooting) {
                    val isRotationStop = mMotionManager.getTotalGyroAbsHf() < 0.01
                    if (mShotAngleSum >= 360 && mAngle.value == 0) {
                        isShooting = false
                        mSoundPlayer.playCompSound()
                        mMotionManager.stop()
                    } else if (mAngle.value == mShotAngleSum && isRotationStop) {
                        mShotAngleSum += mRotationAngle
                        mCamera360Manager?.takePhoto()
                    }
                }
            }
        }
    }

    private val mCamera360ManagerListener = object : Camera360Manager.Camera360ManagerListener {
        override fun initFinish() {
            initCamera360Manager()
            setFilePath()

            CameraInfoService.getSuperWideRangeCameraInfo()?.let {
                isUltraWide.value = true
            }
        }

        override fun startCameraConfigured(context: Context) {
            val exposureBracketList = Camera360Manager.EXPOSURE_BRACKET_LIST
            val range = mCamera360Manager?.getAeCompensationRange() ?: Range(0,0)
            val step = mCamera360Manager?.getAeCompensationStep() ?: 0.0

            val itemArray = mutableListOf(application.getString(R.string.exposure_bracket_mode_none))

            exposureBracketList.forEach {
                if (it.max() == 0) return@forEach
                if (it.max() <= (range.upper * step)) {
                    itemArray.add("+-${it.max()} EV")
                }
            }

            mExposureBracketModeList.value = itemArray
        }

        override fun takePhotoSuccess() {
            Log.d(TAG, "takePhotoSuccess")
            mMatterportAxisManager.sendAngle(mRotationAngle.toUByte())
        }

        override fun takePhotoError() {
            Log.d(TAG, "takePhotoError")
            isShooting = false
        }

    }

}