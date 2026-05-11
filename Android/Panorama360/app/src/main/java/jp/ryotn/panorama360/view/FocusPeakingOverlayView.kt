package jp.ryotn.panorama360.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

class FocusPeakingOverlayView(context: Context) : View(context) {
    private var mOverlayBitmap: Bitmap? = null
    private val mPaint = Paint()

    fun updateOverlay(bitmap: Bitmap?) {
        mOverlayBitmap = bitmap
        post { invalidate() }
    }

    fun clearOverlay() {
        mOverlayBitmap = null
        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mOverlayBitmap?.let { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(bitmap, srcRect, dstRect, mPaint)
        }
    }
}
