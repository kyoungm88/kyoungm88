package com.neighbor.objectdetector

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.neighbor.objectdetector.util.Utils
import kotlinx.android.synthetic.main.activity_mnist.*
import java.io.IOException


class MnistActivity : CameraActivity() {

    private var TAG = MnistActivity::class.java.simpleName
    private var mClassifier: Classifier? = null

    private val MSG_HANDLE_DETECT = 100
    private val MSG_HANDLE_DETECT_DELAY = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_mnist)

        super.onCreate(savedInstanceState)

        initUI()

    }

    private fun initUI() {
        try {
            mClassifier = Classifier(this)
        } catch (e: IOException) {
            Log.e(TAG, "init(): Failed to create tflite model", e)
        }

    }

    override fun onResume() {
        super.onResume()

        startDetect()
    }

    override fun onPause() {
        super.onPause()

        stopDetect()
    }

    private fun startDetect() {
        mHandler.sendEmptyMessageDelayed(MSG_HANDLE_DETECT, MSG_HANDLE_DETECT_DELAY)
    }

    private fun stopDetect() {
        mHandler.removeMessages(MSG_HANDLE_DETECT)
    }

    private fun renderResult(result: Result?, bitmap: Bitmap) {
        Log.d(TAG, "[renderResult] result prediction : ${result?.getNumber()} cost : ${result?.getTimeCost()} ")
        runOnUiThread({
            tvPredictionResult.text = result?.getNumber().toString()
            tvCostResult.text = result?.getTimeCost().toString()
            tvProbabilityResult.text = result?.getProbability().toString()

            if (ivInvert.background != null) {
                (ivInvert.background as BitmapDrawable).bitmap.recycle()
            }

            ivInvert.background = BitmapDrawable(resources, bitmap)
        })
    }

    private val mHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            MSG_HANDLE_DETECT -> {
                takePicture(object: CameraPreview.PictureCallback {
                    override fun onPicture(bitmap: Bitmap, width: Int, height: Int) {
                        // The model is trained on images with black background and white font
                        val image = Utils.exportToBitmap(bitmap, Classifier.DIM_IMG_SIZE_WIDTH, Classifier.DIM_IMG_SIZE_HEIGHT)
                        val inverted = ImageUtil.invert(image)
//                        val inverted = ImageUtil.tempInvert()
                        Log.d(TAG, "[onPicture] inverted width : ${inverted.width}, height : ${inverted.height}")
                        val result = mClassifier?.classify(inverted)
                        renderResult(result, inverted)

                        startDetect()
                    }
                })
                true
            }

            else -> {
                true
            }
        }
    }


}