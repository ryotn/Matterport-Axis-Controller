package jp.ryotn.panorama360

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object PreferencesManager {
    private const val TAG = "PreferencesManager"
    private const val KEY_USE_GYRO = "USE_GYRO"
    private const val KEY_SAVE_DIR_PATH = "SAVE_DIR_PATH"
    private const val KEY_EXPOSURE_BRACKET_MODE = "EXPOSURE_BRACKET_MODE"

    private lateinit var mDefaultPreference: SharedPreferences

    private var isGyro = false
    private var mSaveDirPath: String? = null
    private var mExposureBracketMode = 0

    fun setUp(context: Context){
        mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(context)
        isGyro = getUseGyro(isForce = true)
        mSaveDirPath = getSaveDirPth(isForce = true)
        mExposureBracketMode = getExposureBracketMode(isForce = true)
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

    fun getSaveDirPth(isForce: Boolean = false): String? {
        return if (isForce) {
            mDefaultPreference.getString(KEY_SAVE_DIR_PATH, null)
        } else {
            mSaveDirPath
        }
    }

    fun putSaveDirPath(value: String) {
        mSaveDirPath = value
        mDefaultPreference.edit {
            putString(KEY_SAVE_DIR_PATH, value)
        }
    }

    fun getExposureBracketMode(isForce: Boolean = false): Int {
        return if (isForce) {
            mDefaultPreference.getInt(KEY_EXPOSURE_BRACKET_MODE, 99)
        } else {
            mExposureBracketMode
        }
    }

    fun putExposureBracketMode(value: Int) {
        mExposureBracketMode = value
        mDefaultPreference.edit {
            putInt(KEY_EXPOSURE_BRACKET_MODE, value)
        }
    }
}