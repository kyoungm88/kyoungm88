package com.neighbor.objectdetector

import android.graphics.*


class ImageUtil {

    companion object {
        private val INVERT = ColorMatrix(
                floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f))
        private val COLOR_FILTER = ColorMatrixColorFilter(INVERT)

        fun invert(image: Bitmap): Bitmap {
            val inverted = Bitmap.createBitmap(image.width, image.height,
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(inverted)
            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.setColorFilter(COLOR_FILTER)
            canvas.drawBitmap(image, 0f, 0f, paint)
            return inverted
        }

        fun tempInvert(): Bitmap {
            val inverted = Bitmap.createBitmap(28, 28,
                    Bitmap.Config.ARGB_8888)

            val canvas = Canvas(inverted)
            canvas.drawColor(Color.BLACK)
            val paint = Paint()
            paint.color = Color.WHITE
            paint.strokeWidth = 10f
            paint.textSize = 20f
            canvas.drawText("8", 8f, 20f, paint)
            return inverted
        }
    }
}