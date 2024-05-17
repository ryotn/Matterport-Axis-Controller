package jp.ryotn.panorama360.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import jp.ryotn.panorama360.model.MainViewModel


internal class CameraView(context: Context) : TextureView(context), SurfaceTextureListener {
    private var mViewModel: MainViewModel? = null

    init {
        surfaceTextureListener = this
    }

    fun setModel(model: MainViewModel) {
        mViewModel = model
        model.setViewFinder(this)
        if (this.isAvailable) {
            mViewModel?.initCamera360Manager()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mViewModel?.initCamera360Manager()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
}