package jp.ryotn.panorama360

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.extensions.ExtensionMode
import androidx.camera.view.PreviewView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.preference.PreferenceManager
import kotlinx.coroutines.DelicateCoroutinesApi

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mDefaultPreference: SharedPreferences
    private lateinit var mCamera360Manager: Camera360Manager
    private lateinit var mMatterportAxisManager: MatterportAxisManager
    private lateinit var mSoundPlayer: SoundPlayer
    private lateinit var mTextState: TextView
    private lateinit var mTextAngle: TextView
    private lateinit var mBtnConnect: Button
    private lateinit var mBtnStart: Button
    private lateinit var mBtnResetAngle: Button
    private lateinit var mBtnTestCapture: Button
    private lateinit var mBtnCreateDir: Button
    private lateinit var mViewFinder: PreviewView
    private lateinit var mTextFocusDistance: TextView
    private lateinit var mSeekBarFocusDistance: SeekBar
    private lateinit var mRadioWideLens: RadioButton
    private lateinit var mRadioUltraWideLens: RadioButton
    private lateinit var mRadioGroupLensSel: RadioGroup
    private lateinit var mRadioModeNormal: RadioButton
    private lateinit var mRadioModeHDR: RadioButton
    private lateinit var mRadioModeNight: RadioButton
    private lateinit var mRadioGroupModeSel: RadioGroup
    private lateinit var mProcessingView: FrameLayout

    private var isShooting: Boolean = false
    private var mShotAngleSum: Int = 0
    private var mRotationAngle: Int = 30

    private val permissionResults = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: Map<String, Boolean> ->
        if (result.all { it.value }) {
            Toast.makeText(this, "全部権限取れた", Toast.LENGTH_SHORT).show()

            mBtnConnect.isEnabled = true
            mCamera360Manager = Camera360Manager(context = this)
            mCamera360Manager.mListener = mCamera360ManagerListener
            getFilePath()
        } else {
            Toast.makeText(this, "全部権限取れなかった！", Toast.LENGTH_SHORT).show()
        }
    }

    private val mStartForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                Log.d(TAG, "get File Save Path $uri")
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                mDefaultPreference.edit {
                    putString("uri", uri.toString())
                }

                mCamera360Manager.setOutputDirectory(uri)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissionResults.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.CAMERA))
        mProcessingView = findViewById(R.id.processingView)
        mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(this)
        mMatterportAxisManager = MatterportAxisManager(context = this)
        mSoundPlayer = SoundPlayer(context = this)

        mTextState = findViewById(R.id.txtState)
        mTextAngle = findViewById(R.id.txtAngle)
        mTextAngle.text = getString(R.string.angle,0)
        mBtnStart = findViewById(R.id.btnStart)
        mBtnResetAngle = findViewById(R.id.btnReset)
        mBtnConnect = findViewById(R.id.btnConnect)
        mBtnTestCapture = findViewById(R.id.btnTestCapture)
        mBtnCreateDir = findViewById(R.id.btnCreateDir)
        mViewFinder = findViewById(R.id.viewFinder)
        mTextFocusDistance = findViewById(R.id.txtFocusDistance)
        mSeekBarFocusDistance = findViewById(R.id.seekFocusDistance)
        mRadioWideLens = findViewById(R.id.radioWide)
        mRadioUltraWideLens = findViewById(R.id.radioUltra)
        mRadioGroupLensSel = findViewById(R.id.radioGroupLensSel)
        mRadioModeNormal = findViewById(R.id.radioNormal)
        mRadioModeHDR = findViewById(R.id.radioHDR)
        mRadioModeNight = findViewById(R.id.radioNight)
        mRadioGroupModeSel = findViewById(R.id.radioGroupModeSel)

        mBtnConnect.setOnClickListener {
            if (mMatterportAxisManager.isConnected()) {
                mMatterportAxisManager.disconnect()
                mBtnConnect.text = getString(R.string.connect)
            } else {
                mMatterportAxisManager.connect()
                mBtnConnect.text = getString(R.string.connecting)
            }
            mBtnConnect.isEnabled = false
        }
        mBtnConnect.isEnabled = false

        mBtnStart.setOnClickListener {
            startCapture()
        }

        mBtnResetAngle.setOnClickListener {
            mMatterportAxisManager.resetAngle()
            mBtnResetAngle.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed( {
                mBtnResetAngle.isEnabled = true
            }, 1000)
        }
        mBtnResetAngle.isEnabled = false

        mSeekBarFocusDistance.progress = (resources.getString(R.string.default_focus_distance).toFloat() * 10).toInt()
        mSeekBarFocusDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mTextFocusDistance.text = (progress / 10.0F).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val focalDistance = seekBar.progress / 10.0F
                mCamera360Manager.setFocusDistance(focalDistance)
            }
        })

        mBtnTestCapture.setOnClickListener {
            mProcessingView.visibility = View.VISIBLE
            mCamera360Manager.takePhoto()
        }

        mBtnCreateDir.setOnClickListener {
            mCamera360Manager.createDir()
        }

        mRadioGroupLensSel.setOnCheckedChangeListener { _, checkedId ->
            mCamera360Manager.stopCamera()
            if (mRadioWideLens.id == checkedId) {
                mRotationAngle = 30
                CameraInfoService.getWideRangeCameraInfo()?.let {
                    changeCamera(it)
                }
            } else if (mRadioUltraWideLens.id == checkedId) {
                mRotationAngle = 60
                CameraInfoService.getSuperWideRangeCameraInfo()?.let {
                    changeCamera(it)
                }
            }
        }

        mRadioGroupModeSel.setOnCheckedChangeListener { _, checkedId ->
            mCamera360Manager.stopCamera()
            var mode: Int? = null
            if (mRadioModeNormal.id == checkedId) {
                mode = null
            } else if (mRadioModeHDR.id == checkedId) {
                mode = ExtensionMode.HDR
            } else if (mRadioModeNight.id == checkedId) {
                mode = ExtensionMode.NIGHT
            }
            mCamera360Manager.startCamera(mViewFinder ,null ,mode)
        }

        mMatterportAxisManager.mListener = mMatterportAxisManagerListener
    }

    private fun changeCamera(extendedCameraInfo: CameraInfoService.ExtendedCameraInfo) {
        mRadioGroupModeSel.check(mRadioModeNormal.id)
        mCamera360Manager.startCamera(mViewFinder, extendedCameraInfo.cameraId)
        mRadioModeHDR.isEnabled = extendedCameraInfo.isHDR
        mRadioModeNight.isEnabled = extendedCameraInfo.isNightMode
    }

    private fun startCapture() {
        if (isShooting || !mMatterportAxisManager.isConnected()) return
        mSoundPlayer.playStartSound()
        mBtnStart.isEnabled = false
        mShotAngleSum = mRotationAngle
        Handler(Looper.getMainLooper()).postDelayed({
            mCamera360Manager.takePhoto()
        }, 500)
        Handler(Looper.getMainLooper()).postDelayed({
            isShooting = true
        }, 1000)
    }

    private fun getFilePath() {
        val uriStr = mDefaultPreference.getString("uri", null)
        if (uriStr.isNullOrEmpty()) {
            getFilePermission()
        } else {
            val dir = DocumentFile.fromTreeUri(this, uriStr.toUri())
            dir?.let {
                if (it.canWrite()) {
                    Log.d(TAG, "保存先のPermission取得済み $uriStr")
                    mCamera360Manager.setOutputDirectory(uriStr.toUri())
                } else {
                    getFilePermission()
                }
            }
        }
    }

    private fun getFilePermission() {
        mStartForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            Log.d(TAG, "keycode:${it.keyCode}")
            if (it.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                it.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if(it.action == KeyEvent.ACTION_UP)startCapture()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val mMatterportAxisManagerListener = object : MatterportAxisManager.MatterportAxisManagerListener {
        override fun connected() {
            GlobalScope.launch(Dispatchers.Main) {
                mBtnConnect.text = getString(R.string.disconnect)
                mTextState.text = getString(R.string.state_connected)
                mBtnConnect.isEnabled = true
                mBtnResetAngle.isEnabled = true
            }
        }

        override fun disconnected() {
            GlobalScope.launch(Dispatchers.Main) {
                mBtnConnect.text = getString(R.string.connect)
                mTextState.text = getString(R.string.state_disconnected)
                mBtnConnect.isEnabled = true
                mBtnResetAngle.isEnabled = false
            }
        }

        override fun receiveAngle() {
            GlobalScope.launch(Dispatchers.Main) {
                val angle = mMatterportAxisManager.getAngle()
                mTextAngle.text = getString(R.string.angle, angle)
                if (isShooting) {
                    if (mShotAngleSum >= 360 && angle == 0) {
                        isShooting = false
                        mBtnStart.isEnabled = true
                        mSoundPlayer.playCompSound()
                    } else if (angle == mShotAngleSum) {
                        mShotAngleSum += mRotationAngle
                        mCamera360Manager.takePhoto()
                    }
                }
            }
        }
    }

    private val mCamera360ManagerListener = object : Camera360Manager.Camera360ManagerListener {
        override fun initFinish() {
            CameraInfoService.getWideRangeCameraInfo()?.let {
                changeCamera(it)
            }

            CameraInfoService.getSuperWideRangeCameraInfo()?.let {
                mRadioUltraWideLens.isEnabled = true
            }
        }

        override fun takePhotoSuccess() {
            Log.d(TAG, "takePhotoSuccess")
            mMatterportAxisManager.sendAngle(mRotationAngle.toUByte())
            mProcessingView.visibility = View.INVISIBLE
        }

        override fun takePhotoError() {
            Log.d(TAG, "takePhotoError")
            isShooting = false
            mBtnStart.isEnabled = true
            mProcessingView.visibility = View.INVISIBLE
        }

    }
}