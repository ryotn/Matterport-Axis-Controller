package jp.ryotn.panorama360

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MatterportAxisManager(context: Context) {
    private val TAG = "MatterportAxisManager"

    //Permission
    private val REQUEST_ENABLE_BT: Int = 1
    private val REQUEST_BT_CONNECT_PERMISSION = 3
    private val REQUEST_BT_SCAN_PERMISSION = 4
    private val REQUEST_MULTI_PERMISSIONS = 5

    private val CONTEXT = context
    private val CONNECT_DEVICE_NAME = "Matterport Axis"
    private val SERVICE_UUID = ParcelUuid.fromString(CONTEXT.getString(R.string.baseUUID,"FFE0"))
    private val WRITE_CHARACTERISTIC_UUID = ParcelUuid.fromString(CONTEXT.getString(R.string.baseUUID,"FFE1"))
    private val NOTIFY_CHARACTERISTIC_UUID = ParcelUuid.fromString(CONTEXT.getString(R.string.baseUUID,"FFE4"))

    private var mBtManager: BluetoothManager
    private var mBtAdapter: BluetoothAdapter
    private var mDeviceAddress: String = ""
    private var isScan = false
    private var mConnected: Boolean = false
    private var mAngle = 0
    /*private var mCentralManager: CBCentralManager!
    private var mPeripheral: CBPeripheral? = nil
    private var mWriteCharacteristic: CBCharacteristic? = nil
    private var mNotifyCharacteristic: CBCharacteristic? = nil*/


    init {
        //PermissionのArrayList
        val requestPermissions = ArrayList<String>()
        //パーミッション要求:BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(CONTEXT, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        //パーミッション要求:BLUETOOTH_SCAN
        if (ContextCompat.checkSelfPermission(CONTEXT, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        }
        //必要なPermission要求を全て出す
        if (requestPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(CONTEXT as AppCompatActivity, requestPermissions.toTypedArray(), REQUEST_MULTI_PERMISSIONS)
        }

        mBtManager = CONTEXT.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBtAdapter = mBtManager.adapter
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (!mBtAdapter.isEnabled) {
            Toast.makeText(CONTEXT,"BTがOFFになってるよ",Toast.LENGTH_SHORT).show()
        } else {
            //アドバタイズしているサービスUUIDでフィルターをかける場合は以下を追加
            if (isScan) {
                mBtAdapter.bluetoothLeScanner.stopScan(mScanCallback)
                isScan = false
            }
            isScan = true
            val scanFilters = listOf<ScanFilter>(ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build())
            val scanSettings = ScanSettings.Builder().build()
            mBtAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback)
        }
    }

    //Bt Scan Callback
    private var mScanCallback: ScanCallback? = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "ScanResult:${result}")
            if (CONNECT_DEVICE_NAME == result.device.name) {
                Log.d(TAG, "Discover target devices")
                mBtAdapter.bluetoothLeScanner.stopScan(this)
                isScan = false
                mDeviceAddress = result.device.address

                //connect(mDeviceAddress)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }
}