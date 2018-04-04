package com.neighbor.objectdetector

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.neighbor.objectdetector.classifier.Classifier
import com.neighbor.objectdetector.util.ImageUtil
import com.neighbor.objectdetector.util.Utils
import kotlinx.android.synthetic.main.activity_mnist.*
import java.io.IOException


class MnistActivity : AppCompatActivity(), Camera2Fragment.Camera2Callback {

    private var TAG = MnistActivity::class.java.simpleName
    private var mClassifier: Classifier? = null

    private var cameraFragment: Camera2Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_mnist)

        cameraFragment = supportFragmentManager.findFragmentById(R.id.camera2Fragment) as Camera2Fragment

        initUI()
    }

    private fun initUI() {
        try {
            mClassifier = Classifier(this)
        } catch (e: IOException) {
            Log.e(TAG, "init(): Failed to create tflite model", e)
        }

    }

    override fun onDestroy() {
        mClassifier?.close()
        super.onDestroy()
    }

    private fun renderResult(result: Result?, bitmap: Bitmap) {
        Log.d(TAG, "[renderResult] result prediction : ${result?.getNumber()} cost : ${result?.getTimeCost()} ")
        runOnUiThread({
            tvPredictionResult.text = result?.getNumber().toString()
            tvCostResult.text = result?.getTimeCost().toString()
            tvProbabilityResult.text = result?.getProbability().toString()
            ivInvert.background = BitmapDrawable(resources, bitmap)
        })
    }

    override fun onCapture(bitmap: Bitmap) {
        Log.d(TAG, "[onPicture]")
        // The model is trained on images with black background and white font
        val image = Utils.exportToBitmap(bitmap, Classifier.DIM_IMG_SIZE_WIDTH, Classifier.DIM_IMG_SIZE_HEIGHT)
        val inverted = ImageUtil.invert(image)
        Log.d(TAG, "[onPicture] inverted width : ${inverted.width}, height : ${inverted.height}")
        val result = mClassifier?.classify(inverted)
        renderResult(result, inverted)
    }
}