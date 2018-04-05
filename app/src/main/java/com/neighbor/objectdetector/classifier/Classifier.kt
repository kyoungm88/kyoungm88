package com.neighbor.objectdetector.classifier

import android.app.Activity
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*


class Classifier {
    private val TAG = Classifier::class.java.simpleName

    private val MODEL_PATH = "mnist.tflite"

    private val DIM_BATCH_SIZE = 1
    private val DIM_PIXEL_SIZE = 1
    private val CATEGORY_COUNT = 10

    companion object {
        val DIM_IMG_SIZE_HEIGHT = 28
        val DIM_IMG_SIZE_WIDTH = 28
    }

    private var mInterpreter: Interpreter
    private var mImgData: ByteBuffer
    private val mImagePixels = IntArray(DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH)
    private val mResult = Array(1) { FloatArray(CATEGORY_COUNT) }

    @Throws(IOException::class)
    constructor(activity: Activity) {
        mInterpreter = Interpreter(loadModelFile(activity))

        mImgData = ByteBuffer.allocateDirect(
                4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH * DIM_PIXEL_SIZE)
        mImgData.order(ByteOrder.nativeOrder())
    }

    fun classify(bitmap: Bitmap): Result {
        convertBitmapToByteBuffer(bitmap)
        val startTime = SystemClock.uptimeMillis()
        mInterpreter.run(mImgData, mResult)
        val endTime = SystemClock.uptimeMillis()
        val timeCost = endTime - startTime
        Log.v(TAG, "run(): result = " + Arrays.toString(mResult[0])
                + ", timeCost = " + timeCost)
        return Result(mResult[0], timeCost)
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.getChannel()
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        mImgData.rewind()

//        Log.d(TAG, "[convertBitmapToByteBuffer] width : ${bitmap.width}, height : ${bitmap.height}")

        bitmap.getPixels(mImagePixels, 0, bitmap.width, 0, 0,
                bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until DIM_IMG_SIZE_WIDTH) {
            for (j in 0 until DIM_IMG_SIZE_HEIGHT) {
                val value = mImagePixels[pixel++]
                mImgData.putFloat(convertToGreyScale(value))
            }
        }
    }

    private fun convertToGreyScale(color: Int): Float {
        return ((color shr 16 and 0xFF) + (color shr 8 and 0xFF) + (color and 0xFF)).toFloat() / 3.0f / 255.0f
    }

    fun close() {
        mInterpreter.close()
        mInterpreter
    }
}