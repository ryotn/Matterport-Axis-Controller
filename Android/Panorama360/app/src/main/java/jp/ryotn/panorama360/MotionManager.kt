package jp.ryotn.panorama360

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

class MotionManager(private val context: Context) : SensorEventListener  {
    companion object {
        private const val TAG = "MotionManager"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensorGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val alpha = 0.4F
    private var isStarted = false
    private var totalGyroAbs = 10.0F
    private var totalGyroAbsHf = 10.0F

    private var grav = FloatArray(3)
    private var gyro = FloatArray(3)

    var mListener: MotionManagerListener? = null

    interface MotionManagerListener {
        fun receivedGyro(values: FloatArray)
        fun receivedGravity(values: FloatArray)
    }

    fun start() {
        if (!isStarted) {
            totalGyroAbs = 10.0F
            totalGyroAbsHf = 10.0F
            isStarted = true
            sensorManager.registerListener(this, sensorGyro, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isStarted = false
    }

    fun getTotalGyroAbs(): Float {
        return totalGyroAbs
    }

    fun getTotalGyroAbsHf(): Float {
        return totalGyroAbsHf
    }

    private fun lowpassFilter(currentValues: FloatArray, newValues: FloatArray): FloatArray {
        val lowpassNewValues = FloatArray(currentValues.size)
        for (i in currentValues.indices) {
            lowpassNewValues[i] = alpha * currentValues[i] + (1 - alpha) * newValues[i]
        }

        return lowpassNewValues
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val sensorType = it.sensor.type
            val accuracy = it.accuracy
            val values = it.values

            // Log.d(TAG, "onSensorChanged SensorType:${sensorType} Accuracy:${accuracy} Values:${values}")

            when (sensorType) {
                Sensor.TYPE_GYROSCOPE -> {
                    val x = abs(values[0])
                    val y = abs(values[1])
                    val z = abs(values[2])
                    totalGyroAbs = x + y + z
                    totalGyroAbsHf = alpha * totalGyroAbs + totalGyroAbsHf * (1 - alpha)
                    // Log.d(TAG, "TYPE_GYROSCOPE totalGyroAbsHf:$totalGyroAbsHf")

                    gyro = lowpassFilter(gyro, values.clone())
                    mListener?.receivedGyro(gyro)
                }

                Sensor.TYPE_GRAVITY -> {
                    grav = lowpassFilter(grav, values.clone())
                    mListener?.receivedGravity(grav)
                }

                else -> {}
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged Sensor:$sensor Accuracy:$accuracy")
    }
}