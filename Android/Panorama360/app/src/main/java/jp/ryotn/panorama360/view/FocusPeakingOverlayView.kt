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
        postInvalidate()
    }

    fun clearOverlay() {
        mOverlayBitmap = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mOverlayBitmap?.let { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            if (bitmap.width > bitmap.height && height > width) {
                canvas.save()
                canvas.rotate(90f, width / 2f, height / 2f)
                val rotatedDstRect = RectF(
                    (width - height) / 2f,
                    (height - width) / 2f,
                    (width + height) / 2f,
                    (height + width) / 2f
                )
                canvas.drawBitmap(bitmap, srcRect, rotatedDstRect, mPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bitmap, srcRect, dstRect, mPaint)
            }
        }
    }
}
