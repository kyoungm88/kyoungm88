package com.neighbor.objectdetector

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.neighbor.objectdetector.classifier.CustomClassifier
import com.neighbor.objectdetector.classifier.ImageClassifier
import com.neighbor.objectdetector.classifier.ImageClassifierQuantizedMobileNet
import com.neighbor.objectdetector.util.ImageUtil
import kotlinx.android.synthetic.main.activity_object.*
import java.io.IOException

class ObjectDetectActivity: AppCompatActivity(), Camera2Fragment.Camera2Callback {

    companion object {
        val TAG = ObjectDetectActivity::class.java.simpleName
    }

    private var cameraFragment: Camera2Fragment? = null
    private var imageLabel: ImageClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_object)

        init()
    }

    private fun init() {
        try {
            imageLabel = ImageClassifierQuantizedMobileNet(this)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        cameraFragment = supportFragmentManager.findFragmentById(R.id.camera2Fragment) as Camera2Fragment
    }

    private fun renderResult(data: String?, bitmap: Bitmap) {
        runOnUiThread({
            data?.let {
                tvObjectResult.text = data
            }
            ivInvert.background = BitmapDrawable(resources, bitmap)
        })
    }

    override fun onDestroy() {
        imageLabel?.close()
        super.onDestroy()
    }


    override fun onCapture(bitmap: Bitmap) {
        Log.d(TAG, "[onCapture]")
        val cropBitmap = ImageUtil.cropCenterBitmap(bitmap)
        val resizeBitmap = ImageUtil.scaleBitmap(cropBitmap, CustomClassifier.DIM_IMG_SIZE_WIDTH, CustomClassifier.DIM_IMG_SIZE_HEIGHT)

        val result = imageLabel?.classifyFrame(resizeBitmap)
        Log.d(TAG, "[onCapture] result : $result")
        renderResult(result, resizeBitmap)
    }

}