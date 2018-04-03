package com.neighbor.objectdetector.util

import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Paint.FILTER_BITMAP_FLAG



class Utils {

    companion object {
        val TAG = Utils::class.java.simpleName

        val MEDIA_TYPE_IMAGE = 1
        val MEDIA_TYPE_VIDEO = 2

        /** Create a file Uri for saving an image or video  */
        fun getOutputMediaFileUri(type: Int): Uri {
            return Uri.fromFile(getOutputMediaFile(type))
        }

        /** Create a File for saving an image or video  */
        fun getOutputMediaFile(type: Int): File? {
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

            val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "MyCameraApp")
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                    return null
                }
            }

            // Create a media file name
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val mediaFile: File
            if (type == MEDIA_TYPE_IMAGE) {
                mediaFile = File(mediaStorageDir.getPath() + File.separator +
                        "IMG_" + timeStamp + ".jpg")
            } else if (type == MEDIA_TYPE_VIDEO) {
                mediaFile = File(mediaStorageDir.getPath() + File.separator +
                        "VID_" + timeStamp + ".mp4")
            } else {
                return null
            }

            return mediaFile
        }

        fun exportToBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
            val rawBitmap = grayScale(bitmap)
//            val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, width, height, true)
            val scaledBitmap = bitmapResize(rawBitmap, width, height)


            bitmap.recycle()
            rawBitmap.recycle()
            return scaledBitmap
        }

//        fun exportToBitmap(view: View): Bitmap {
//            Log.d(TAG, "[exportToBitmap] view width : ${view.width / 2}, height : ${view.height / 2}")
//            val bitmap = Bitmap.createBitmap(view.width / 2, view.height / 2, Bitmap.Config.ARGB_8888)
//            val canvas = Canvas(bitmap)
//            val bgDrawable = view.background
//            if (bgDrawable != null) {
//                bgDrawable.draw(canvas)
//            } else {
//                canvas.drawColor(Color.WHITE)
//            }
//            view.draw(canvas)
//            return bitmap
//        }

        private fun bitmapResize(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
            val scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)

            val ratioX = newWidth / bitmap.width.toFloat()
            val ratioY = newHeight / bitmap.height.toFloat()
            val middleX = newWidth / 2.0f
            val middleY = newHeight / 2.0f

            val scaleMatrix = Matrix()
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)

            val canvas = Canvas(scaledBitmap)
            canvas.matrix = scaleMatrix
            canvas.drawBitmap(bitmap, middleX - bitmap.width / 2, middleY - bitmap.height / 2, Paint(FILTER_BITMAP_FLAG))

            return scaledBitmap

        }

        private fun grayScale(orgBitmap: Bitmap): Bitmap {
            Log.i("gray", "in")
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

                if (gray > 128)
                    gray = 255
                else
                    gray = 0

                pixels[i] = Color.argb(A, gray, gray, gray)

            }
            bmpGrayScale.setPixels(pixels, 0, width, 0, 0, width, height)
            Log.i("gray", "out")
            return bmpGrayScale

        }
    }
}