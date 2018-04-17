package com.neighbor.objectdetector.util

import android.graphics.*
import android.media.Image
import android.util.Log
import java.io.ByteArrayOutputStream


object ImageUtil {

    private const val GRAY_THRESHOLD = 90
    private val TAG = ImageUtil::class.java.simpleName

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

    fun exportToBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val rawBitmap = convertToGrayScale(bitmap)
//            val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, width, height, false)
//            rawBitmap.recycle()
        return rawBitmap
    }

    fun bitmapResize(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)

        val ratioX = newWidth / bitmap.width.toFloat()
        val ratioY = newHeight / bitmap.height.toFloat()
        val middleX = newWidth / 2.0f
        val middleY = newHeight / 2.0f

        val scaleMatrix = Matrix()
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)

        val canvas = Canvas(scaledBitmap)
        canvas.matrix = scaleMatrix
        canvas.drawBitmap(bitmap, middleX - bitmap.width / 2, middleY - bitmap.height / 2, Paint(Paint.FILTER_BITMAP_FLAG))

        return scaledBitmap

    }

    fun cropCenterBitmap(src: Bitmap): Bitmap {
        Log.d(TAG, "[cropCenterBitmap]")
        val width = src.width
        val height = src.height
        var cropSize = width

        var x = 0
        var y = 0

        if (width > height) {
            cropSize = height
        }


        if (width > cropSize)
            x = (width - cropSize) / 2

        if (height > cropSize)
            y = (height - cropSize) / 2

        var cw = cropSize // crop width
        var ch = cropSize // crop height

        if (cropSize > width)
            cw = width

        if (cropSize > height)
            ch = height

        val des = Bitmap.createBitmap(src, x, y, cw, ch)
        src.recycle()
        return des
    }


    fun scaleBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        Log.d(TAG, "[scaleBitmap]")
        val scaleChangeBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        bitmap.recycle()
        return scaleChangeBitmap
    }

    fun convertToGrayScale(orgBitmap: Bitmap): Bitmap {
        val width = orgBitmap.width
        val height = orgBitmap.height
        val size = width * height

        val pixels = IntArray(size)
        orgBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val bmpGrayScale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)


        for (i in 0 until size) {
            val color = pixels[i]

            val A = Color.alpha(color)
            val R = Color.red(color)
            val G = Color.green(color)
            val B = Color.blue(color)
            val gray = (0.2989 * R + 0.5870 * G + 0.1140 * B).toInt()

            pixels[i] = Color.argb(A, gray, gray, gray)

        }
        bmpGrayScale.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmpGrayScale

    }

    fun convertToBlackAndWhite(orgBitmap: Bitmap): Bitmap {
        val width = orgBitmap.width
        val height = orgBitmap.height
        val size = width * height

        val pixels = IntArray(size)
        orgBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val bmpGrayScale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)


        for (i in 0 until size) {
            val color = pixels[i]

            val A = Color.alpha(color)
            val R = Color.red(color)
            val G = Color.green(color)
            val B = Color.blue(color)
            var gray = (0.2989 * R + 0.5870 * G + 0.1140 * B).toInt()

            // 평균 128에서 약간 더 어두운 색상도 흰색으로 표시하게 90으로 변경
            if (gray > GRAY_THRESHOLD)
                gray = 255

            pixels[i] = Color.argb(A, gray, gray, gray)

        }
        bmpGrayScale.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmpGrayScale

    }
}