package jp.ryotn.panorama360.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import jp.ryotn.panorama360.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow

class SettingViewModel(private val application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SettingViewModel"
    }

    private lateinit var mPreferencesManager: PreferencesManager

    val isGyro : MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun init(isPreview: Boolean = false) {
        if (!isPreview) {
            mPreferencesManager = PreferencesManager
            mPreferencesManager.setUp(application.applicationContext)
            isGyro.value = mPreferencesManager.getUseGyro()
        }
    }

    fun putUseGyro(value: Boolean) {
        isGyro.value = value
        mPreferencesManager.putUseGyro(value)
    }

}