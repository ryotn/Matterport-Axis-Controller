package jp.ryotn.panorama360

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

@SuppressLint("MissingPermission")
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
    private var mBtGatt: BluetoothGatt? = null
    private var mDeviceAddress: String = ""
    private var isScan = false
    private var isConnected: Boolean = false
    private var mAngle = 0

    private var mWriteCharacteristic: BluetoothGattCharacteristic? = null
    private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null

    var mListener: MatterportAxisManagerListener? = null

    interface MatterportAxisManagerListener {
        fun connected()
        fun disconnected()
        fun receiveAngle()
    }

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

    fun getAngle() : Int {
        return mAngle
    }

    fun isConnected() : Boolean {
        return isConnected
    }

    fun resetAngle() {
        var zeroDegree = 360 - mAngle
        var delay = 0L
        if (zeroDegree > 0xFF){
            sendAngle(angle = 0xFFu)
            zeroDegree -= 0xFF
            delay = 500L
        }
        Handler(Looper.getMainLooper()).postDelayed( {
            sendAngle(angle = zeroDegree.toUByte())
        }, delay)
    }

    fun connect() {
        if (!mBtAdapter.isEnabled) {
            Toast.makeText(CONTEXT,"BTがOFFになってるよ",Toast.LENGTH_SHORT).show()
        } else {
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

    fun connectDevice(address: String) {
        val device: BluetoothDevice = mBtAdapter.getRemoteDevice(address)
        mBtGatt = device.connectGatt(CONTEXT, false, mGattCallback)
    }
    fun disconnect() {
        mBtGatt?.let { gatt ->
            if (mNotifyCharacteristic != null) {
                gatt.setCharacteristicNotification(mNotifyCharacteristic, false)
                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mNotifyCharacteristic?.let {
                        gatt.writeCharacteristic(
                            it,
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    }
                } else {*/
                val descriptor = mNotifyCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                //}
            }
            Log.d(TAG, "disconnect")
            gatt.disconnect()
        }

    }

    fun sendAngle(angle: UByte) {
        val sendData = ByteArray(6)
        sendData[2] = angle.toByte()
        sendData[5] = angle.toByte()
        mWriteCharacteristic?.value = sendData
        mBtGatt?.writeCharacteristic(mWriteCharacteristic)
    }

    //Bt Scan Callback
    private var mScanCallback: ScanCallback? = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "ScanResult:${result}")
            if (CONNECT_DEVICE_NAME == (result.scanRecord?.deviceName ?: "")) {
                Log.d(TAG, "Discover target devices")
                mBtAdapter.bluetoothLeScanner.stopScan(this)
                isScan = false
                mDeviceAddress = result.device.address

                connectDevice(mDeviceAddress)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private var mGattCallback: BluetoothGattCallback? = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true
                mListener?.connected()
                gatt?.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (mBtGatt != null) {
                    mBtGatt?.close()
                    mBtGatt = null
                }
                isConnected = false
                mListener?.disconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.forEach { service ->
                    if (service.uuid == SERVICE_UUID.uuid) {
                        Log.d(TAG,"Found Service")
                        mNotifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID.uuid)
                        mWriteCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID.uuid)

                        gatt.setCharacteristicNotification(mNotifyCharacteristic, true)
                        val descriptor = mNotifyCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if (characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID.uuid) {
                mAngle = (value[2].toUByte().toInt() * 256) + value[3].toUByte().toInt()
                mListener?.receiveAngle()
                Log.d(TAG, "received notify value:${value.toHexString()} angle: $mAngle")
            }
        }

    }
}