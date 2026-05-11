package jp.ryotn.panorama360.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import jp.ryotn.panorama360.PreferencesManager
import jp.ryotn.panorama360.camera.Camera360Manager
import kotlinx.coroutines.flow.MutableStateFlow

class SettingViewModel(private val application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SettingViewModel"
    }

    private lateinit var mPreferencesManager: PreferencesManager

    val isGyro : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isFocusPeaking : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val focusPeakingThreshold : MutableStateFlow<Float> = MutableStateFlow(Camera360Manager.DEFAULT_EDGE_DETECTION_THRESHOLD.toFloat())

    fun init(isPreview: Boolean = false) {
        if (!isPreview) {
            mPreferencesManager = PreferencesManager
            mPreferencesManager.setUp(application.applicationContext)
            isGyro.value = mPreferencesManager.getUseGyro()
            isFocusPeaking.value = mPreferencesManager.getUseFocusPeaking()
            focusPeakingThreshold.value = mPreferencesManager.getFocusPeakingThreshold()
        }
    }

    fun putUseGyro(value: Boolean) {
        isGyro.value = value
        mPreferencesManager.putUseGyro(value)
    }

    fun putUseFocusPeaking(value: Boolean) {
        isFocusPeaking.value = value
        mPreferencesManager.putUseFocusPeaking(value)
    }

    fun putFocusPeakingThreshold(value: Float) {
        val roundedValue = value.toInt().toFloat()
        focusPeakingThreshold.value = roundedValue
        mPreferencesManager.putFocusPeakingThreshold(roundedValue)
    }

}
