package com.neighbor.objectdetector

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import com.neighbor.objectdetector.classifier.Classifier
import com.neighbor.objectdetector.classifier.Result
import com.neighbor.objectdetector.util.ImageUtil
import kotlinx.android.synthetic.main.activity_mnist.*
import java.io.IOException


class MnistActivity : AppCompatActivity(), Camera2Fragment.Camera2Callback {

    private var TAG = MnistActivity::class.java.simpleName
    private var mClassifier: Classifier? = null

    private var cameraFragment: Camera2Fragment? = null
    private val listMnist = ArrayList<Result>()
    private var mnistAdapter: MnistRecyclerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_mnist)

        initData()
        initUI()
    }

    private fun initData() {
        try {
            mClassifier = Classifier(this)
        } catch (e: IOException) {
            Log.e(TAG, "init(): Failed to create tflite model", e)
        }

        cameraFragment = supportFragmentManager.findFragmentById(R.id.camera2Fragment) as Camera2Fragment
        cameraFragment?.initImageFrameSize(Classifier.DIM_IMG_SIZE_WIDTH, Classifier.DIM_IMG_SIZE_HEIGHT)
    }

    private fun initUI() {

        mnistAdapter = MnistRecyclerAdapter(this, listMnist)
        list_mnist.adapter = mnistAdapter

        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.reverseLayout = true
        linearLayoutManager.stackFromEnd = true
        list_mnist.layoutManager = linearLayoutManager
    }

    override fun onDestroy() {
        mClassifier?.close()
        super.onDestroy()
    }

    private fun renderResult(result: Result, bitmap: Bitmap) {
        Log.d(TAG, "[renderResult] result prediction : ${result.getNumber()} cost : ${result.getTimeCost()} ")
        runOnUiThread({
            tvPredictionResult.text = result.getNumber().toString()
            tvCostResult.text = result.getTimeCost().toString()
            tvProbabilityResult.text = result.getProbability().toString()
            ivInvert.background = BitmapDrawable(resources, bitmap)

            if (listMnist.size == 0 || listMnist[listMnist.size - 1].getNumber() != result.getNumber()) {
                listMnist.add(result)
            }

            mnistAdapter?.notifyDataSetChanged()
        })
    }

    override fun onCapture(bitmap: Bitmap) {
        Log.d(TAG, "[onCapture]")

        val blackAndWhiteBitmap = ImageUtil.convertToBlackAndWhite(bitmap)
        // The model is trained on images with black background and white font
        val inverted = ImageUtil.invert(blackAndWhiteBitmap)
//        Log.d(TAG, "[onPicture] inverted width : ${inverted.width}, height : ${inverted.height}")
        val result = mClassifier?.classify(inverted)!!
        val grayscale = ImageUtil.convertToGrayScale(inverted)
//        val invert = ImageUtil.invert(grayscale)
        renderResult(result, grayscale)
    }
}