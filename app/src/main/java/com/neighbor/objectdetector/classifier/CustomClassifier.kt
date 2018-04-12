package com.neighbor.objectdetector.classifier

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import java.util.Map

class CustomClassifier {
    private val TAG = CustomClassifier::class.java.simpleName

    private val MODEL_PATH = "cifar10.tflite"
    private val LABEL_PATH = "label.txt"

    private val DIM_BATCH_SIZE = 1
    private val DIM_PIXEL_SIZE = 3
    private val CATEGORY_COUNT = 10

    private val RESULTS_TO_SHOW = 3
    private val FILTER_STAGES = 3
    private val FILTER_FACTOR = 0.4f

    companion object {
        val DIM_IMG_SIZE_WIDTH = 32
        val DIM_IMG_SIZE_HEIGHT = 32
    }

    private var mInterpreter: Interpreter
    private val mLabelList: List<String>
    private var mImgData: ByteBuffer
    private val mImagePixels = IntArray(DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH)
    private val mResult = Array(1) { FloatArray(CATEGORY_COUNT) }

    private var mFilterLabelProbArray: Array<FloatArray>? = null
    private val sortedLabels = PriorityQueue<Map.Entry<String, Float>>(
            RESULTS_TO_SHOW,
            Comparator<Map.Entry<String, Float>> {
                o1, o2 -> o1.value.compareTo(o2.value)
            })


    @Throws(IOException::class)
    constructor(activity: Activity) {
        mInterpreter = Interpreter(loadModelFile(activity))
        mLabelList = loadLabelList(activity)
        mImgData = ByteBuffer.allocateDirect(
                4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_HEIGHT * DIM_IMG_SIZE_WIDTH * DIM_PIXEL_SIZE)
        mImgData.order(ByteOrder.nativeOrder())
        mFilterLabelProbArray = Array(FILTER_STAGES) { FloatArray(CATEGORY_COUNT) }
//        mResult = Array(1) { ByteArray(CATEGORY_COUNT) }
    }

    fun classify(bitmap: Bitmap): String {
        convertBitmapToByteBuffer(bitmap)
        val startTime = SystemClock.uptimeMillis()
        mInterpreter.run(mImgData, mResult)
        val endTime = SystemClock.uptimeMillis()
        val timeCost = endTime - startTime
        Log.v(TAG, "run(): result = " + Arrays.toString(mResult[0])
                + ", timeCost = " + timeCost)

        applyFilter()
        var textToShow = printTopKLabels()
        textToShow = java.lang.Long.toString(endTime - startTime) + "ms" + textToShow
        return textToShow
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /** Reads label list from Assets.  */
    @Throws(IOException::class)
    private fun loadLabelList(activity: Activity): List<String> {
        val labelList = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(activity.assets.open(LABEL_PATH)))

        for (line in reader.readLines()) {
            labelList.add(line)
        }

        reader.close()
        return labelList
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
        mImgData.putFloat(Color.red(pixelValue).toFloat())
        mImgData.putFloat(Color.green(pixelValue).toFloat())
        mImgData.putFloat(Color.blue(pixelValue).toFloat())
    }

    private fun applyFilter() {
        val numLabels = CATEGORY_COUNT

        // Low pass filter `labelProbArray` into the first stage of the filter.
        for (j in 0 until numLabels) {
            mFilterLabelProbArray!![0][j] += FILTER_FACTOR * (getProbability(j) - mFilterLabelProbArray!![0][j])
        }
        // Low pass filter each stage into the next.
        for (i in 1 until FILTER_STAGES) {
            for (j in 0 until numLabels) {
                mFilterLabelProbArray!![i][j] += FILTER_FACTOR * (mFilterLabelProbArray!![i - 1][j] - mFilterLabelProbArray!![i][j])
            }
        }

        // Copy the last stage filter output back to `labelProbArray`.
        for (j in 0 until numLabels) {
            setProbability(j, mFilterLabelProbArray!![FILTER_STAGES - 1][j])
        }
    }

    private fun getProbability(labelIndex: Int): Float {
        return mResult[0][labelIndex]
    }

    private fun setProbability(labelIndex: Int, value: Number) {
        mResult[0][labelIndex] = value.toFloat()
    }

    private fun getNormalizedProbability(labelIndex: Int): Float {
        return String.format("%1.2f", mResult[0][labelIndex]).toFloat()
    }

    private fun printTopKLabels(): String {
        for (i in 0 until CATEGORY_COUNT) {
            sortedLabels.add(
                    AbstractMap.SimpleEntry(mLabelList[i], getProbability(i)) as Map.Entry<String, Float>)
            if (sortedLabels.size > RESULTS_TO_SHOW) {
                sortedLabels.poll()
            }
        }
        var textToShow = ""
        val size = sortedLabels.size
        for (i in 0 until size) {
            val label = sortedLabels.poll()
            textToShow = String.format("\n%s: %4.2f", label.key, label.value) + textToShow
        }
        return textToShow
    }

    fun close() {
        mInterpreter.close()
    }
}