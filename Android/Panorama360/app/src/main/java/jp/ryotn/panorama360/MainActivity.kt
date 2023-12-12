package jp.ryotn.panorama360

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var mCameraManager: CameraManager
    private lateinit var mMatterportAxisManager: MatterportAxisManager
    private lateinit var mTextState: TextView
    private lateinit var mTextAngle: TextView
    private lateinit var mBtnConnect: Button
    private lateinit var mBtnTestBtn: Button
    private lateinit var mBtnResetAngle: Button
    private lateinit var mViewFinder: PreviewView
    private val permissionResults = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: Map<String, Boolean> ->
        if (result.all { it.value }) {
            Toast.makeText(this, "全部権限取れた", Toast.LENGTH_SHORT).show()

            mBtnConnect.isEnabled = true
            mCameraManager.startCamera(mViewFinder)
        } else {
            Toast.makeText(this, "全部権限取れなかった！", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissionResults.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.CAMERA))
        mCameraManager = CameraManager(context = this)
        mMatterportAxisManager = MatterportAxisManager(context = this)

        mTextState = findViewById(R.id.txtState)
        mTextAngle = findViewById(R.id.txtAngle)
        mTextAngle.text = getString(R.string.angle,0)
        mBtnTestBtn = findViewById(R.id.btnSetAngle)
        mBtnResetAngle = findViewById(R.id.btnReset)
        mBtnConnect = findViewById(R.id.btnConnect)
        mViewFinder = findViewById(R.id.viewFinder)

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

        mBtnTestBtn.setOnClickListener {
            mMatterportAxisManager.sendAngle(10u)
        }

        mBtnResetAngle.setOnClickListener {
            mMatterportAxisManager.resetAngle()
            mBtnResetAngle.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed( {
                mBtnResetAngle.isEnabled = true
            }, 1000)
        }
        mBtnResetAngle.isEnabled = false

        mMatterportAxisManager.mListener = mMatterportAxisManagerListener
    }

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
            }
        }

    }
}