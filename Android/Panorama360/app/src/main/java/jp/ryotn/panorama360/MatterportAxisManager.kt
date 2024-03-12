package jp.ryotn.panorama360

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import java.util.UUID

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class MatterportAxisManager(context: Context) {
    companion object {
        private const val TAG = "MatterportAxisManager"
        private const val CONNECT_DEVICE_NAME = "Matterport Axis"
    }

    private val mContext = context
    private val mServiceUUID = ParcelUuid.fromString(mContext.getString(R.string.baseUUID,"FFE0"))
    private val mWriteCharacteristicUUID = ParcelUuid.fromString(mContext.getString(R.string.baseUUID,"FFE1"))
    private val mNotifyCharacteristicUUID = ParcelUuid.fromString(mContext.getString(R.string.baseUUID,"FFE4"))
    private val mBtManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val mBtAdapter = mBtManager.adapter

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
            Toast.makeText(mContext,"BTがOFFになってるよ",Toast.LENGTH_SHORT).show()
        } else {
            if (isScan) {
                mBtAdapter.bluetoothLeScanner.stopScan(mScanCallback)
                isScan = false
            }
            isScan = true
            val scanFilters = listOf<ScanFilter>(ScanFilter.Builder().setServiceUuid(mServiceUUID).build())
            val scanSettings = ScanSettings.Builder().build()
            mBtAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback)
        }
    }

    fun connectDevice(address: String) {
        val device: BluetoothDevice = mBtAdapter.getRemoteDevice(address)
        mBtGatt = device.connectGatt(mContext, false, mGattCallback)
    }
    fun disconnect() {
        mBtGatt?.let { gatt ->
            if (mNotifyCharacteristic != null) {
                gatt.setCharacteristicNotification(mNotifyCharacteristic, false)
                val descriptor = mNotifyCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    descriptor?.let {
                        gatt.writeDescriptor(
                            it,
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        )
                    }
                } else {
                    descriptor?.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
            Log.d(TAG, "disconnect")
            gatt.disconnect()
        }

    }

    fun sendAngle(angle: UByte) {
        val sendData = ByteArray(6)
        sendData[2] = angle.toByte()
        sendData[5] = angle.toByte()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mWriteCharacteristic?.let {
                mBtGatt?.writeCharacteristic(
                    it,
                    sendData,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            }
        } else {
            mWriteCharacteristic?.value = sendData
            mBtGatt?.writeCharacteristic(mWriteCharacteristic)
        }
    }

    //Bt Scan Callback
    private var mScanCallback: ScanCallback? = object : ScanCallback() {
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
                    if (service.uuid == mServiceUUID.uuid) {
                        Log.d(TAG,"Found Service")
                        mNotifyCharacteristic = service.getCharacteristic(mNotifyCharacteristicUUID.uuid)
                        mWriteCharacteristic = service.getCharacteristic(mWriteCharacteristicUUID.uuid)

                        gatt.setCharacteristicNotification(mNotifyCharacteristic, true)
                        val descriptor = mNotifyCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            descriptor?.let {
                                gatt.writeDescriptor(
                                    it,
                                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                )
                            }
                        } else {
                            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
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
            if (characteristic.uuid == mNotifyCharacteristicUUID.uuid) {
                mAngle = (value[2].toUByte().toInt() * 256) + value[3].toUByte().toInt()
                mListener?.receiveAngle()
                Log.d(TAG, "received notify value:${value.toHexString()} angle: $mAngle")
            }
        }

    }
}