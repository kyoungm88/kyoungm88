package com.neighbor.objectdetector

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import kotlinx.android.synthetic.main.layout_textureview.*


open class TempCameraActivity : AppCompatActivity() {

    companion object {
        val TAG = TempCameraActivity::class.java.simpleName
        val PERMISSIONS_REQUEST_CODE = 1000
    }

    private var mPreview: CameraPreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasPermissions(PERMISSIONS)) {
                init()
            } else {
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE)
            }
        } else {
            init()
        }
    }

    fun init() {
        mPreview = CameraPreview(this, cameraTextureView)
    }

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "[onStart]")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "[onResume]")
        mPreview?.onResume()
    }

    override fun onPause() {
        super.onPause()

        Log.d(TAG, "[onPause]")
        mPreview?.onPause()
    }

    override fun onStop() {
        super.onStop()

        Log.d(TAG, "[onStop]")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        newConfig?.let {config ->
            mPreview?.cameraOrientation(config.orientation)
        }
    }

    private val PERMISSIONS_REQUEST_CODE = 1000
    private var PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private fun hasPermissions(permissions: Array<String>): Boolean {
        var result: Int
        for (param in permissions) {
            result = ContextCompat.checkSelfPermission(this, param)

            if (result == PackageManager.PERMISSION_DENIED) {
                return false
            }
        }

        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> if (grantResults.size > 0) {

                val cameraPermissionGrant = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val writePermissionGrant = grantResults[1] == PackageManager.PERMISSION_GRANTED

                if (!cameraPermissionGrant || !writePermissionGrant) {
                    showDialogForPermission("앱에서 실행하려면 퍼미션을 허가하셔야 합니다.")
                    return
                } else {
                    init()
                }
            }
            else -> {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showDialogForPermission(msg: String) {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("권한 알림")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("예") { dialog, which -> requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE) }
                .setNegativeButton("아니오") { dialog, which -> finish() }
                .create().show()

    }

    fun takePicture(callback: CameraPreview.PictureCallback) {
        mPreview?.takePicture(callback)
    }

}
