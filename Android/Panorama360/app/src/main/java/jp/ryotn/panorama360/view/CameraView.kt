package jp.ryotn.panorama360.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.FrameLayout
import jp.ryotn.panorama360.model.MainViewModel


class CameraView(context: Context) : FrameLayout(context), SurfaceTextureListener {
    private val mTextureView: TextureView = TextureView(context)
    val mFocusPeakingOverlay: FocusPeakingOverlayView = FocusPeakingOverlayView(context)
    private var mViewModel: MainViewModel? = null

    init {
        addView(mTextureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(mFocusPeakingOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mTextureView.surfaceTextureListener = this
    }

    fun setModel(model: MainViewModel) {
        mViewModel = model
        model.setViewFinder(mTextureView)
        if (mTextureView.isAvailable) {
            mViewModel?.initCamera360Manager()
        }
    }

    fun updateFocusPeaking(bitmap: Bitmap?) {
        mFocusPeakingOverlay.updateOverlay(bitmap)
    }

    fun clearFocusPeaking() {
        mFocusPeakingOverlay.clearOverlay()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mViewModel?.initCamera360Manager()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mViewModel?.stopCamera()
        return false
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
}