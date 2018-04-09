package com.neighbor.objectdetector.classifier

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
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

class CustomClassifier {
    private val TAG = CustomClassifier::class.java.simpleName

    private val MODEL_PATH = ""

    private val DIM_BATCH_SIZE = 1
    private val DIM_PIXEL_SIZE = 3
    private val CATEGORY_COUNT = 10

    companion object {
        val DIM_IMG_SIZE_WIDTH = 32
        val DIM_IMG_SIZE_HEIGHT = 32
    }

    private var mInterpreter: Interpreter
    private var mImgData: ByteBuffer
    private val mImagePixels = IntArray(DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH)
    private val mResult = Array(1) { FloatArray(CATEGORY_COUNT) }

    @Throws(IOException::class)
    constructor(activity: Activity) {
        mInterpreter = Interpreter(loadModelFile(activity))

        mImgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE * DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH * DIM_PIXEL_SIZE)
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
                addPixelValue(value)
            }
        }
    }

    private fun addPixelValue(pixelValue: Int) {
        mImgData.put(Color.red(pixelValue).toByte())
        mImgData.put(Color.green(pixelValue).toByte())
        mImgData.put(Color.blue(pixelValue).toByte())
    }

    fun close() {
        mInterpreter.close()
    }
}