package com.neighbor.objectdetector.classifier

import android.app.Activity
import java.io.IOException
import kotlin.experimental.and

/**
 * This classifier works with the quantized MobileNet model.
 */
class ImageClassifierQuantizedMobileNet
/**
 * Initializes an `ImageClassifier`.
 *
 * @param activity
 */
@Throws(IOException::class)
internal constructor(activity: Activity) : ImageClassifier(activity) {

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs.
     * This isn't part of the super class, because we need a primitive array here.
     */
    private var labelProbArray: Array<ByteArray>? = null

    override// you can download this file from
    // https://storage.googleapis.com/download.tensorflow.org/models/tflite/mobilenet_v1_224_android_quant_2017_11_08.zip
    val modelPath: String
        get() = "mobilenet_quant_v1_224.tflite"

    override val labelPath: String
        get() = "labels_mobilenet_quant_v1_224.txt"

    override val imageSizeX: Int
        get() = 224

    override val imageSizeY: Int
        get() = 224

    override// the quantized model uses a single byte only
    val numBytesPerChannel: Int
        get() = 1

    init {
        labelProbArray = Array(1) { ByteArray(numLabels) }
    }

    override fun addPixelValue(pixelValue: Int) {
        imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
        imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
        imgData!!.put((pixelValue and 0xFF).toByte())
    }

    override fun getProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex].toFloat()
    }

    override fun setProbability(labelIndex: Int, value: Number) {
        labelProbArray!![0][labelIndex] = value.toByte()
    }

    override fun getNormalizedProbability(labelIndex: Int): Float {
        return (labelProbArray!![0][labelIndex] and 0xff.toByte()) / 255.0f
    }

    override fun runInference() {
        tflite!!.run(imgData!!, labelProbArray!!)
    }
}
