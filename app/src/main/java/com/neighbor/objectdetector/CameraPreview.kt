package com.neighbor.objectdetector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import java.util.*
import java.util.concurrent.Semaphore


class CameraPreview(context: Context, textureView: TextureView) : Thread() {

    companion object {
        private val TAG = CameraPreview::class.java.simpleName
    }

    private val mContext: Context
    private val mTextureView: TextureView

    private var mMap: StreamConfigurationMap? = null
    private var mPreviewSize: Size? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var mImageReader: ImageReader? = null
    private var mSensorOrientation: Int = 0

    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    private lateinit var mPreviewRequest: CaptureRequest

    private var mOrientation: Int

    private val ORIENTATIONS = SparseIntArray()

    init {
        mContext = context
        mTextureView = textureView

        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)

        mOrientation = mContext.resources.configuration.orientation
        cameraOrientation(mOrientation)
    }

    private fun getBackFacingCameraId(cameraManager: CameraManager): String {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        return ""
    }

    fun openCamera() {
        val cameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = getBackFacingCameraId(cameraManager)
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            mMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            mPreviewSize = mMap?.getOutputSizes(SurfaceTexture::class.java)!![0]
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            val permission = ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)

            if (permission == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(mContext as Activity, arrayOf(Manifest.permission.CAMERA), CameraActivity.PERMISSIONS_REQUEST_CODE)
            } else {
                cameraManager.openCamera(cameraId, mStateCallback, null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    val mSurfaceTextureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "[onSurfaceTextureSizeChanged]")
            configureTransform(width, height)
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

    val mStateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            Log.d(TAG, "[onOpened]")
            mCameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(p0: CameraDevice?) {
            Log.d(TAG, "[onDisconnected]")
        }

        override fun onError(p0: CameraDevice?, p1: Int) {
            Log.d(TAG, "[onError]")
        }

    }

    private fun startPreview() {
        Log.d(TAG, "[startPreview]")
        if (mCameraDevice == null || !mTextureView.isAvailable || mPreviewSize == null) {
            Log.d(TAG, "[startPreview] fail")
            return
        }

        val texture = mTextureView.surfaceTexture
        if (texture == null) {
            Log.d(TAG, "[startPreview] texture is null")
            return
        }

        mPreviewSize?.let {
            texture.setDefaultBufferSize(it.width, it.height)
        }

        val surface = Surface(texture)

        mCameraDevice?.let {
            mPreviewBuilder = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        }

        mPreviewBuilder?.addTarget(surface)

        try {
            mCameraDevice?.createCaptureSession(Arrays.asList(surface), object: CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(p0: CameraCaptureSession?) {
                    Log.d(TAG, "[onConfigureFailed]")
                }

                override fun onConfigured(session: CameraCaptureSession?) {
                    Log.d(TAG, "[onConfigured]")
                    mPreviewSession = session
                    updatePreview()
                }

            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun updatePreview() {
        if (mCameraDevice == null) {
            Log.d(TAG, "[updatePreview] Camera Device null")
        }

        mPreviewBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val thread = HandlerThread("CameraPreview")
        thread.start()

        val backgroundHandler = Handler(thread.looper)

        mPreviewSession?.setRepeatingRequest(mPreviewBuilder?.build(), null, backgroundHandler)
    }

    private fun setSurfaceTextureListener() {
        mTextureView.surfaceTextureListener = mSurfaceTextureListener
    }

    fun onResume() {
        Log.d(TAG, "[onResume]")
        setSurfaceTextureListener()
    }

    private val mCameraOpenCloseLock = Semaphore(1)

    fun onPause() {
        Log.d(TAG, "[onPause]")

        try {
            mCameraOpenCloseLock.acquire()
            mCameraDevice?.close()

        } catch (e: InterruptedException) {
            Log.d(TAG, "[onPause] interrupted exception")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    fun cameraOrientation(orientation: Int) {
        mOrientation = orientation
//        configureTransform()
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = (mContext as Activity).windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize?.height!!.toFloat(), mPreviewSize?.width!!.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize?.height!!,
                    viewWidth.toFloat() / mPreviewSize?.width!!)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
    }

    fun takePicture(callback: PictureCallback) {
        if (null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null, return")
            return
        }

        try {
            var jpegSizes: Array<Size>? = null
            if (mMap != null) {
                jpegSizes = mMap!!.getOutputSizes(PixelFormat.RGBA_8888)
            }
            var width = 640
            var height = 480
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = jpegSizes[0].width
                height = jpegSizes[0].height
            }

            Log.d(TAG, "[takePicture] width : $width, height : $height")
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

            val outputSurfaces = ArrayList<Surface>(2)

            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(mTextureView.surfaceTexture))

            val captureBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(reader.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            // Orientation
            val rotation = (mContext as Activity).windowManager.defaultDisplay.rotation
            captureBuilder?.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            val readerListener = object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        val buffer = image!!.getPlanes()[0].getBuffer()
                        val bytes = ByteArray(buffer.capacity())
                        buffer.get(bytes)

                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        callback.onPicture(bitmap, width, height)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (image != null) {
                            image!!.close()
                            reader.close()
                        }
                    }
                }
            }

            val thread = HandlerThread("MNISTPicture")
            thread.start()
            val backgroundHandler = Handler(thread.looper)
            reader.setOnImageAvailableListener(readerListener, backgroundHandler)

            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d(TAG, "[onCaptureCompleted]")
                    startPreview()
                }

            }

            mCameraDevice?.createCaptureSession(outputSurfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        Log.d(TAG, "[onConfigured] capture")
                        session.capture(captureBuilder?.build(), captureListener, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }

                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "[onConfigureFailed] session")
                }
            }, backgroundHandler)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    interface PictureCallback {
        fun onPicture(bitmap: Bitmap, width: Int, height: Int)
    }
}