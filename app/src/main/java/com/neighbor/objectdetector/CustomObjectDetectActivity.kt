package com.neighbor.objectdetector

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.neighbor.objectdetector.classifier.Custom128Classifier
import com.neighbor.objectdetector.classifier.CustomClassifier
import com.neighbor.objectdetector.util.ImageUtil
import kotlinx.android.synthetic.main.activity_custom_object.*
import java.io.IOException

class CustomObjectDetectActivity: AppCompatActivity(), Camera2Fragment.Camera2Callback {

    companion object {
        val TAG = CustomObjectDetectActivity::class.java.simpleName
    }

    private var cameraFragment: Camera2Fragment? = null
    private var classifier: CustomClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_custom_object)

        init()
    }

    private fun init() {
        try {
            classifier = CustomClassifier(this)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        cameraFragment = supportFragmentManager.findFragmentById(R.id.camera2Fragment) as Camera2Fragment
    }

    override fun onDestroy() {
        classifier?.close()
        super.onDestroy()
    }

    private fun renderResult(data: String?, bitmap: Bitmap) {
        runOnUiThread({
            tvObjectResult.text = data
            ivInvert.background = BitmapDrawable(resources, bitmap)
        })
    }

    override fun onCapture(bitmap: Bitmap) {
        Log.d(TAG, "[onCapture]")

        val cropBitmap = ImageUtil.cropCenterBitmap(bitmap)
        val resizeBitmap = ImageUtil.scaleBitmap(cropBitmap, CustomClassifier.DIM_IMG_SIZE_WIDTH, CustomClassifier.DIM_IMG_SIZE_HEIGHT)
        val data = classifier?.classify(resizeBitmap)
        renderResult(data, resizeBitmap)
    }
}