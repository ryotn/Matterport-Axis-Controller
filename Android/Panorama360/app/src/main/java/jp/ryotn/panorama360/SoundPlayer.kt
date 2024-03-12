package jp.ryotn.panorama360

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundPlayer(context: Context) {
    private val mContext = context

    private var mSoundPool: SoundPool
    private var mStartSoundId = 0
    private var mCompSoundId = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        mSoundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(2)
            .build()
        mStartSoundId = mSoundPool.load(mContext, R.raw.start, 1)
        mCompSoundId = mSoundPool.load(mContext, R.raw.comp, 1)
    }

    fun playStartSound() {
        playSound(mStartSoundId)
    }

    fun playCompSound() {
        playSound(mCompSoundId)
    }

    private fun playSound(id: Int) {
        mSoundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f)
    }
}