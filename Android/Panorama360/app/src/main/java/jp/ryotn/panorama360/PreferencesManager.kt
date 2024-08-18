package jp.ryotn.panorama360

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object PreferencesManager {
    private const val TAG = "PreferencesManager"
    private const val KEY_USE_GYRO = "USE_GYRO"

    private lateinit var mDefaultPreference: SharedPreferences

    private var isGyro = false

    fun setUp(context: Context){
        mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(context)
        isGyro = getUseGyro(isForce = true)
    }

    fun getUseGyro(isForce: Boolean = false): Boolean {
        return if (isForce) {
            mDefaultPreference.getBoolean(KEY_USE_GYRO, true)
        } else {
            isGyro
        }
    }

    fun putUseGyro(value: Boolean) {
        isGyro = value
        mDefaultPreference.edit {
            putBoolean(KEY_USE_GYRO, value)
        }
    }
}