package com.neighbor.objectdetector.util

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream


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

        fun imageToByteArray(image: Image): ByteArray? {
            var data: ByteArray? = null
            if (image.getFormat() === ImageFormat.JPEG) {
                val planes = image.getPlanes()
                val buffer = planes[0].getBuffer()
                data = ByteArray(buffer.capacity())
                buffer.get(data)
                return data
            } else if (image.getFormat() === ImageFormat.YUV_420_888) {
                data = NV21toJPEG(
                        YUV_420_888toNV21(image),
                        image.getWidth(), image.getHeight())
            }
            return data
        }

        private fun YUV_420_888toNV21(image: Image): ByteArray {
            val nv21: ByteArray
            val yBuffer = image.getPlanes()[0].getBuffer()
            val uBuffer = image.getPlanes()[1].getBuffer()
            val vBuffer = image.getPlanes()[2].getBuffer()

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            nv21 = ByteArray(ySize + uSize + vSize)

            //U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            return nv21
        }

        private fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
            return out.toByteArray()
        }
    }
}