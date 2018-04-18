package com.neighbor.objectdetector

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.view.*
import kotlinx.android.synthetic.main.fragment_camera.*
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/** Basic fragments for the Camera. */
class Camera2Fragment : Fragment() {

    /** Tag for the {@link Log}. */
    companion object {
        private val TAG = Camera2Fragment::class.java.simpleName
        private val FRAGMENT_DIALOG = "dialog"
        private val HANDLE_THREAD_NAME = "CameraBackground"
        private val PERMISSIONS_REQUEST_CODE = 1

        /** Max preview width that is guaranteed by Camera2 API */
        private val MAX_PREVIEW_WIDTH = 1920

        /** Max preview height that is guaranteed by Camera2 API */
        private val MAX_PREVIEW_HEIGHT = 1080
    }

    private var cameraCallback: Camera2Callback? = null

    private val lock = Object()
    private var runClassifier = false
    private var checkedPermissions = false
    private var runBackgroundThread = false

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "[onSurfaceTextureAvailable]")
            if (runBackgroundThread) {
                openCamera(width, height)
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            Log.d(TAG, "[onSurfaceTextureDestroyed]")
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** ID of the current [CameraDevice].  */
    private var cameraId: String? = null

    /** A [CameraCaptureSession] for camera preview.  */
    private var captureSession: CameraCaptureSession? = null

    /** A reference to the opened [CameraDevice].  */
    private var cameraDevice: CameraDevice? = null

    /** The [android.util.Size] of camera preview.  */
    private var previewSize: Size? = null

    /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.  */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = currentCameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            currentCameraDevice.close()
            cameraDevice = null
            val activity = activity
            activity?.finish()
        }
    }

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.  */
    private var backgroundHandler: Handler? = null

    /** An [ImageReader] that handles image capture.  */
    private var imageReader: ImageReader? = null

    /** [CaptureRequest.Builder] for the camera preview  */
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    /** [CaptureRequest] generated by [.previewRequestBuilder]  */
    private var previewRequest: CaptureRequest? = null

    /** A [Semaphore] to prevent the app from exiting before closing the camera.  */
    private val cameraOpenCloseLock = Semaphore(1)

    /** A [CameraCaptureSession.CaptureCallback] that handles events related to capture.  */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
        }
    }

    /**
     * Resizes image.
     *
     * Attempting to use too large a preview size could  exceed the camera bus' bandwidth limitation,
     * resulting in gorgeous previews but the storage of garbage capture data.
     *
     * Given `choices` of `Size`s supported by a camera, choose the smallest one that is
     * at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size, and
     * whose aspect ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param textureViewWidth The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth The maximum width that can be chosen
     * @param maxHeight The maximum height that can be chosen
     * @param aspectRatio The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = ArrayList<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth
                    && option.height <= maxHeight
                    && option.height == option.width * h / w) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size > 0) {
            return Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            return Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size")
            return choices[0]
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        try {
            cameraCallback = context as Camera2Callback
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }

    }
    /** Layout the preview and buttons.  */
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onStart() {
        super.onStart()

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        Log.d(TAG, "[onResume] texture available : ${textureView.isAvailable}")
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()

        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val activity = activity
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // // For still image captures, we use the largest available size.
                val jpegSizes: Array<Size>? = map.getOutputSizes(ImageFormat.JPEG)

                var imageWidth = width
                var imageHeight = height
                if (jpegSizes != null && 0 < jpegSizes.size) {
                    imageWidth = jpegSizes[0].width
                    imageHeight = jpegSizes[0].height
                }
                imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, /*maxImages*/ 2)

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity.windowManager.defaultDisplay.rotation

                /* Orientation of the camera sensor */
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }

                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                previewSize = chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        jpegSizes?.get(0)!!)

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView!!.setAspectRatio(previewSize!!.width, previewSize!!.height)
                } else {
                    textureView!!.setAspectRatio(previewSize!!.height, previewSize!!.width)
                }

                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access Camera", e)
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    private fun getRequiredPermissions(): Array<String?> {
        try {
            val ps = arrayOfNulls<String?>(2)
            ps[0] = Manifest.permission.CAMERA
            ps[1] = Manifest.permission.WRITE_EXTERNAL_STORAGE
            return if (ps.size > 0) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
        } catch (e: Exception) {
            return arrayOfNulls(0)
        }

    }

    /** Opens the camera specified by [Camera2Fragment.cameraId].  */
    private fun openCamera(width: Int, height: Int) {
        Log.d(TAG, "[openCamera]")
        if (!checkedPermissions && !allPermissionsGranted()) {
            requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE)
            return
        } else {
            checkedPermissions = true
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val activity = activity
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)

            if (permission == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.PERMISSIONS_REQUEST_CODE)
            } else {
                manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open Camera", e)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity!!, permission!!) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /** Closes the current [CameraDevice].  */
    private fun closeCamera() {
        try {
            Log.d(TAG, "[closeCamera]")
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            if (null != imageReader) {
                imageReader!!.close()
                imageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        Log.d(TAG, "[startBackgroundThread]")
        runBackgroundThread = true
        backgroundThread = HandlerThread(HANDLE_THREAD_NAME)
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
        synchronized(lock) {
            runClassifier = true
        }
        backgroundHandler!!.post(periodicClassify)
    }

    /** Stops the background thread and its [Handler].  */
    private fun stopBackgroundThread() {
        Log.d(TAG, "[stopBackgroundThread]")
        runBackgroundThread = false
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
            synchronized(lock) {
                runClassifier = false
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted when stopping background thread", e)
        }

    }

    /** Takes photos and classify them periodically.  */
    private val periodicClassify = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (runClassifier && cameraCallback != null) {
                    getFrame()
                }
            }

            if (runBackgroundThread)
                backgroundHandler!!.post(this)
        }
    }

    /** Creates a new [CameraCaptureSession] for camera preview.  */
    private fun createCameraPreviewSession() {
        if (cameraDevice == null) {
            return
        }

        try {
            val texture = textureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice!!.createCaptureSession(
                    Arrays.asList(surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder!!.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder!!.build()
                                captureSession!!.setRepeatingRequest(
                                        previewRequest!!, captureCallback, backgroundHandler)
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to set up config to capture Camera", e)
                            }

                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            Log.w(TAG, "onConfigureFailed")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to preview Camera", e)
        }

    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`. This
     * method should be called after the camera preview size is determined in setUpCameraOutputs and
     * also the size of `textureView` is fixed.
     *
     * @param viewWidth The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize!!.height,
                    viewWidth.toFloat() / previewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90F * (rotation - 2), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
    }

    /** Classifies a frame from the preview stream.  */
    private fun getFrame() {
        if (activity == null || cameraDevice == null) {
//            Log.w(TAG,"Uninitialized Classifier or invalid context.")
            return
        }
//        val bitmap = textureView!!.getBitmap(imageFrameWidth, imageFrameHeight)
        val bitmap = textureView!!.getBitmap(textureView.measuredWidth / 4, textureView.measuredHeight / 4)
        cameraCallback?.onCapture(bitmap)
//        bitmap.recycle()
    }

    /** Compares two `Size`s based on their areas.  */
    private class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                    lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    /** Shows an error message dialog.  */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val activity = getActivity()
            return AlertDialog.Builder(activity)
                    .setMessage(getArguments()!!.getString(ARG_MESSAGE))
                    .setPositiveButton(
                            android.R.string.ok,
                            DialogInterface.OnClickListener { dialogInterface, i -> activity!!.finish() })
                    .create()
        }

        companion object {

            private val ARG_MESSAGE = "message"

            fun newInstance(message: String): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.setArguments(args)
                return dialog
            }
        }
    }

    interface Camera2Callback {
        fun onCapture(bitmap: Bitmap)
    }
}
