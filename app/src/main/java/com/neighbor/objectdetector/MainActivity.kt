package com.neighbor.objectdetector

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSIONS_REQUEST_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()
    }

    private fun initUI() {
        btnMnist.setOnClickListener({
            startActivity(getIntent(MnistActivity::class.java))
        })

        btnObject.setOnClickListener({
            startActivity(getIntent(ObjectDetectActivity::class.java))
        })

        btnCustomObject32.setOnClickListener({
            startActivity(getIntent(CustomObjectDetectActivity::class.java))
        })

        btnCustomObject128.setOnClickListener({
            startActivity(getIntent(CustomObjectDetect128Activity::class.java))
        })
    }

    private fun getIntent(cls: Class<*>): Intent {
        val intent = Intent(this, cls)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return intent
    }
}
