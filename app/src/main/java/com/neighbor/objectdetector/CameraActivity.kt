package com.neighbor.objectdetector

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import kotlinx.android.synthetic.main.layout_textureview.*
import java.nio.ByteBuffer
import java.util.*


open class CameraActivity : AppCompatActivity() {

    companion object {
        val TAG = CameraActivity::class.java.simpleName
        val PERMISSIONS_REQUEST_CODE = 1000
    }

    private var cameraId: String? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation: Int = 0

    private val ORIENTATIONS = SparseIntArray()

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    val mStateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            Log.d(TAG, "[onOpened]")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(cameraDevice: CameraDevice?) {
            Log.d(TAG, "[onDisconnected]")
            cameraDevice?.close()
        }

        override fun onError(cameraDevice: CameraDevice?, p1: Int) {
            Log.d(TAG, "[onError]")
            cameraDevice?.close()
        }

    }

    val mSurfaceTextureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "[onSurfaceTextureSizeChanged]")
        }

        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {
//            Log.d(TAG, "[onSurfaceTextureUpdated]")
        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean {
            Log.d(TAG, "[onSurfaceTextureDestroyed]")
            return false
        }

        override fun onSurfaceTextureAvailable(p0: SurfaceTexture?, p1: Int, p2: Int) {
            Log.d(TAG, "[onSurfaceTextureAvailable]")
            openCamera()
        }

    }

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
        cameraTextureView.surfaceTextureListener = mSurfaceTextureListener
    }

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "[onStart]")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "[onResume]")
        startBackgroundThread()

        if (cameraTextureView.isAvailable) {
            openCamera()
        } else {
            cameraTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread?.looper)
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()

        Log.d(TAG, "[onPause]")

    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        cameraDevice?.close()
    }

    override fun onStop() {
        super.onStop()

        Log.d(TAG, "[onStop]")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

//        newConfig?.let {config ->
//            mPreview?.cameraOrientation(config.orientation)
//        }
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
        if (cameraDevice == null) {
            return
        }

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraDevice?.id)
            var jpegSizes: Array<Size>? = null

            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)
            }

            var width = 640
            var height = 480
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = jpegSizes[0].width / 2
                height = jpegSizes[0].height / 2
            }
            Log.d(TAG, "[takePicture] width : $width, height : $height")

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)

            val outputSurfaces = ArrayList<Surface>(2)
            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(cameraTextureView.surfaceTexture))

            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(reader.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder?.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            val readerListener = object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    Log.d(TAG, "[onImageAvailable]")
                    var image: Image? = null
                    try {
                        image = reader.acquireNextImage()
                        val buffer = image!!.getPlanes()[0].getBuffer()
                        val bytes = ByteArray(buffer.capacity())
                        buffer.get(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        callback.onPicture(bitmap, width, height)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (image != null) {
                            image.close()
                            reader.close()
                        }
                    }
                }
            }

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d(TAG, "[onCaptureCompleted]")
                    createCameraPreview()
                }

            }

            cameraDevice?.createCaptureSession(outputSurfaces, object: CameraCaptureSession.StateCallback() {

                override fun onConfigured(cameraCaptureSession: CameraCaptureSession?) {
                    Log.d(TAG, "[onConfigured]")
                    try {
                        cameraCaptureSession?.capture(captureBuilder?.build(), captureListener, mBackgroundHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(p0: CameraCaptureSession?) {
                    Log.d(TAG, "[onConfigureFailed]")
                }

            }, mBackgroundHandler)

        } catch (e: CameraAccessException) {

        }
    }

    private fun createCameraPreview() {
        try {
            val texture = cameraTextureView.surfaceTexture
            texture.setDefaultBufferSize(previewSize?.width!!, previewSize?.height!!)

            val surface = Surface(texture)
            previewBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(Arrays.asList(surface), object: CameraCaptureSession.StateCallback() {

                override fun onConfigured(cameraCaptureSession: CameraCaptureSession?) {
                    if (cameraDevice == null) {
                        return
                    }

                    previewSession = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(p0: CameraCaptureSession?) {
                    Log.d(TAG, "[onConfigureFailed] Chage")
                }

            }, null)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) {
            Log.d(TAG, "[updatePreview] Error")
        }

        previewBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            previewSession?.setRepeatingRequest(previewBuilder?.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun openCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            previewSize = map?.getOutputSizes(SurfaceTexture::class.java)!![0]
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CameraActivity.PERMISSIONS_REQUEST_CODE)
            } else {
                cameraManager.openCamera(cameraId, mStateCallback, null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

}
