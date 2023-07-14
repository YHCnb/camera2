package com.example.mycamera2.fragment.photo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.computeExifOrientation
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.mycamera2.MainActivity
import com.example.mycamera2.R
import com.example.mycamera2.ZoomUtil
import com.example.mycamera2.databinding.FragmentPhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

class PhotoFragment2 : Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var photoViewModel: PhotoViewModel

    /** Android ViewBinding */
    private var _fragmentPhotoBinding: FragmentPhotoBinding? = null

    private val fragmentPhotoBinding get() = _fragmentPhotoBinding!!

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * Preview surface
     */
    private lateinit var previewSurface: Surface

    /**
     * Size of preview
     */
    private lateinit var previewSize: Size

    /**
     * Camera extension characteristics for the current camera device.
     */
    private lateinit var extensionCharacteristics: CameraExtensionCharacteristics

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] ，用于处理cameraThread */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen
     *  拍摄时动画效果
     * */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            fragmentPhotoBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            fragmentPhotoBinding.overlay.postDelayed({
                // Remove white flash animation
                fragmentPhotoBinding.overlay.background = null
            }, MainActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** [HandlerThread] where all buffer reading operations run ，图片读取线程 */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread]，图片读取线程的handler */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters
     *  用来向相机设备发送获取图像的请求
     * */
    private lateinit var cameraCaptureSession: CameraCaptureSession

    /** 横屏与竖屏检测 / Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    /**
     * Flag whether we should restart preview after an extension switch.
     */
    private var restartPreview = false

    //与Extension相关的变量
    /** Track current extension type and index. */
    private var currentExtension = -1
    private var currentExtensionIdx = -1
    /** The current camera extension session. */
    private lateinit var cameraExtensionSession: CameraExtensionSession
    /** 缩放比例 */
    private var zoomRatio: Float = ZoomUtil.minZoom()
    /** Gesture detector used for tap to focus */
    private val tapToFocusListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            return tapToFocus(event)
        }
    }

    /**
     * 缩放手势监听 /Define a scale gesture detector to respond to pinch events and call setZoom on Camera.Parameters.
     */
    private val scaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!hasZoomSupport()) {
                return false
            }

            // In case there is any focus happening, stop it.
            cancelPendingAutoFocus()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Set the zoom level
            startZoom(detector.scaleFactor)
            return true
        }
    }

    /**
     * Used to dispatch auto focus cancel after a timeout
     */
    private val tapToFocusTimeoutHandler = Handler(Looper.getMainLooper())

    /**
     * Trivial capture callback implementation.
     * 一个回调对象，用于跟踪提交到相机设备的进度
     */
    private val captureCallbacks: CameraExtensionSession.ExtensionCaptureCallback =
        object : CameraExtensionSession.ExtensionCaptureCallback() {
            override fun onCaptureStarted(
                session: CameraExtensionSession, request: CaptureRequest,
                timestamp: Long
            ) {
                Log.v(TAG, "onCaptureStarted ts: $timestamp")
            }

            override fun onCaptureProcessStarted(
                session: CameraExtensionSession,
                request: CaptureRequest
            ) {
                Log.v(TAG, "onCaptureProcessStarted")
            }

            override fun onCaptureResultAvailable(
                session: CameraExtensionSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                Log.v(TAG, "onCaptureResultAvailable")
                if (request.tag == AUTO_FOCUS_TAG) {
                    Log.v(TAG, "Auto focus region requested")

                    // Consider listening for auto focus state such as auto focus locked
                    // 考虑监听自动对焦状态，例如自动对焦锁定
                    cameraExtensionSession.stopRepeating()
                    val autoFocusRegions = request.get(CaptureRequest.CONTROL_AF_REGIONS)
                    submitRequest(
                        CameraDevice.TEMPLATE_PREVIEW,
                        previewSurface,
                        true,
                    ) { builder ->
                        builder.apply {
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                            set(CaptureRequest.CONTROL_AF_REGIONS, autoFocusRegions)
                        }
                    }

                    queueAutoFocusReset()
                }
            }

            override fun onCaptureFailed(
                session: CameraExtensionSession,
                request: CaptureRequest
            ) {
                Log.v(TAG, "onCaptureProcessFailed")
            }

            override fun onCaptureSequenceCompleted(
                session: CameraExtensionSession,
                sequenceId: Int
            ) {
                Log.v(TAG, "onCaptureProcessSequenceCompleted: $sequenceId")
            }

            override fun onCaptureSequenceAborted(
                session: CameraExtensionSession,
                sequenceId: Int
            ) {
                Log.v(TAG, "onCaptureProcessSequenceAborted: $sequenceId")
            }
        }

    /**
     * A list of supported extensions
     */
    private val supportedExtensions = ArrayList<Int>()
    //SurfaceTextureListener的4个接口函数重写
    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int, height: Int
    ) {
        initializeCamera()
    }
    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int, height: Int
    ) {}
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        return true
    }
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity()
        photoViewModel =
            ViewModelProvider(this).get(PhotoViewModel::class.java)
        photoViewModel.setDefaultId(cameraManager)

        _fragmentPhotoBinding = FragmentPhotoBinding.inflate(inflater, container, false)
        val root: View = fragmentPhotoBinding.root

        return root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //疑惑？？？？？？？
        fragmentPhotoBinding.captureButton.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }

        fragmentPhotoBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                // 返回最大的预览尺寸，Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                    fragmentPhotoBinding.viewFinder.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                Log.d(TAG, "View finder size: ${fragmentPhotoBinding.viewFinder.width} x ${fragmentPhotoBinding.viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                fragmentPhotoBinding.viewFinder.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )

                // 获得尺寸后才初始化相机，To ensure that size is set, initialize camera in the view's thread
                view.post { initializeCamera() }
            }
        })

        //对texture设置手动对焦和缩放手势的Listener
        fragmentPhotoBinding.texture.surfaceTextureListener = this

        val tapToFocusGestureDetector = GestureDetector(requireContext(), tapToFocusListener)
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), scaleGestureListener)
        fragmentPhotoBinding.texture.setOnTouchListener { _, event ->
            tapToFocusGestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }

        extensionCharacteristics = cameraManager.getCameraExtensionCharacteristics(photoViewModel.cameraId.value!!)
        characteristics = cameraManager.getCameraCharacteristics(photoViewModel.cameraId.value!!)
        supportedExtensions.addAll(extensionCharacteristics.supportedExtensions)
        if (currentExtension == -1) {
            currentExtension = supportedExtensions[0]
            currentExtensionIdx = 0
            fragmentPhotoBinding.switchButton.text = getExtensionLabel(currentExtension)
        }
        //切换相机模式
        fragmentPhotoBinding.switchButton.setOnClickListener { v ->
            if (v.id == R.id.switch_button) {
                lifecycleScope.launch(Dispatchers.IO) {
                    currentExtensionIdx = (currentExtensionIdx + 1) % supportedExtensions.size
                    currentExtension = supportedExtensions[currentExtensionIdx]
                    requireActivity().runOnUiThread {
                        fragmentPhotoBinding.switchButton.text = getExtensionLabel(currentExtension)
                        restartPreview = true   //切换模式要刷新预览画面
                    }
                    try {
                        cameraExtensionSession.stopRepeating()
                        cameraExtensionSession.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera failure when closing camera extension")
                    }
                }
            }
        }
        // Listen to the capture button
        fragmentPhotoBinding.captureButton.setOnClickListener {

            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false

            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                clearPendingAutoFocusReset()

                takePhoto().use { result ->
                    Log.d(TAG, "Result received: $result")

                    // Save the result to disk
                    val output = saveResult(result)
                    Log.d(TAG, "Image saved: ${output.absolutePath}")

                    // If the result is a JPEG file, update EXIF metadata with orientation info
                    // EXIF为在JPEG的基础上插入数码信息
                    if (output.extension == "jpg") {
                        val exif = ExifInterface(output.absolutePath)
                        exif.setAttribute(
                            ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                        exif.saveAttributes()
                        Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                    }

                    // 图片展示/ Display the photo taken to user
                    lifecycleScope.launch(Dispatchers.Main) {
                        navController.navigate(PhotoFragmentDirections
                            .actionPhotoToImageViewerFragment(
                                output.absolutePath,
                                result.orientation,
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && result.format == ImageFormat.DEPTH_JPEG
                            )
                        )
                    }
                }

                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
            }
        }
        // React to user touching the capture button
//        fragmentPhotoBinding.captureButton.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        clearPendingAutoFocusReset()
//                        takePhoto()
//                    }
//                }
//            }
//            true
//        }
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        // Open the selected camera
        camera = openCamera(cameraManager, photoViewModel.cameraId.value!!, cameraHandler)

        startPreview()
    }

    /**
     * Starts the camera preview.
     */
    @Synchronized
    private fun   startPreview() {
//        // Initialize an image reader which will be used to capture still photos
//        previewSize = pickPreviewResolution(cameraManager, photoViewModel.cameraId.value!!)
////        val size = characteristics.get(
////            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
////            .getOutputSizes(photoViewModel.pixelFormat.value!!).maxByOrNull { it.height * it.width }!!
//        imageReader = ImageReader.newInstance(
//            previewSize.width, previewSize.height, photoViewModel.pixelFormat.value!!, IMAGE_BUFFER_SIZE)
//        //用cameraCaptureSession还是cameraExtensionSession？  TODO
//
//        // Creates list of Surfaces where the camera will output frames
//        val targets = listOf(fragmentPhotoBinding.viewFinder.holder.surface, imageReader.surface)
//
//        // Start a capture session using our open camera and list of Surfaces where frames will go
//        cameraCaptureSession = createCaptureSession(camera, targets, cameraHandler)
//
//        val captureRequest = camera.createCaptureRequest(
//            CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(fragmentPhotoBinding.viewFinder.holder.surface) }
//
//        // This will keep sending the capture request as frequently as possible until the
//        // session is torn down or session.stopRepeating() is called
//        // 重复发送请求
//        cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        if (!fragmentPhotoBinding.texture.isAvailable) {
            return
        }
        //fragmentPhotoBinding.viewFinder.holder.surface还是fragmentPhotoBinding.texture.surfaceTexture TODO
        val texture = fragmentPhotoBinding.texture.surfaceTexture
        previewSize = pickPreviewResolution(cameraManager, photoViewModel.cameraId.value!!)
        texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(texture)
        val yuvColorEncodingSystemSizes = extensionCharacteristics.getExtensionSupportedSizes(
            currentExtension, ImageFormat.YUV_420_888
        )
        val jpegSizes = extensionCharacteristics.getExtensionSupportedSizes(
            currentExtension, ImageFormat.JPEG
        )
        val stillFormat = if (jpegSizes.isEmpty()) ImageFormat.YUV_420_888 else ImageFormat.JPEG
        val stillCaptureSize = if (jpegSizes.isEmpty()) yuvColorEncodingSystemSizes[0] else jpegSizes[0]
        imageReader = ImageReader.newInstance(
            stillCaptureSize.width,
            stillCaptureSize.height, stillFormat, 1
        )
        imageReader.setOnImageAvailableListener(
            { reader: ImageReader ->
                var output: OutputStream
                try {
                    reader.acquireLatestImage().use { image ->
                        val file = File(
                            requireActivity().getExternalFilesDir(null),
                            if (image.format == ImageFormat.JPEG) "frame.jpg" else "frame.yuv"
                        )
                        output = FileOutputStream(file)
                        output.write(getDataFromImage(image))
                        output.close()
                        Toast.makeText(
                            requireActivity(), "Frame saved at: " + file.path,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, cameraHandler
        )
        val outputConfig = ArrayList<OutputConfiguration>()
        outputConfig.add(OutputConfiguration(imageReader.surface))
        outputConfig.add(OutputConfiguration(previewSurface))
        val extensionConfiguration = ExtensionSessionConfiguration(
            currentExtension, outputConfig,
            Dispatchers.IO.asExecutor(), object : CameraExtensionSession.StateCallback() {
                override fun onClosed(session: CameraExtensionSession) {
                    if (restartPreview) {
                        imageReader.close()
                        restartPreview = false
                        startPreview()
                    } else {
                        camera.close()
                    }
                }

                override fun onConfigured(session: CameraExtensionSession) {
                    cameraExtensionSession = session
                    submitRequest(
                        CameraDevice.TEMPLATE_PREVIEW,
                        previewSurface,
                        true
                    ) { request ->
                        request.apply {
                            set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
                        }
                    }
                }

                override fun onConfigureFailed(session: CameraExtensionSession) {
                    Toast.makeText(
                        requireActivity(),
                        "Failed to start camera extension preview.",
                        Toast.LENGTH_SHORT
                    ).show()
                    requireActivity().finish()
                }
            }
        )
        try {
            camera.createExtensionSession(extensionConfiguration)
        } catch (e: CameraAccessException) {
            Toast.makeText(
                requireActivity(), "Failed during extension initialization!.",
                Toast.LENGTH_SHORT
            ).show()
            requireActivity().finish()
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine)
     *  打开相机
     * */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

                override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Pick a preview resolution that is both close/same as the display size and supported by camera
     * and extensions.
     * 找出一个与显示器大小接近，且相机和拓展都支持的预览分辨率
     */
    @Throws(CameraAccessException::class)
    private fun pickPreviewResolution(manager: CameraManager, cameraId: String) : Size {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val textureSizes = map!!.getOutputSizes(
            SurfaceTexture::class.java
        )
        val displaySize = Point()
        val displayMetrics = requireActivity().resources.displayMetrics
        displaySize.x = displayMetrics.widthPixels
        displaySize.y = displayMetrics.heightPixels
        if (displaySize.x < displaySize.y) {
            displaySize.x = displayMetrics.heightPixels
            displaySize.y = displayMetrics.widthPixels
        }
        val displayArRatio = displaySize.x.toFloat() / displaySize.y
        val previewSizes = ArrayList<Size>()
        //选择最接近的textureSize
        for (sz in textureSizes) {
            val arRatio = sz.width.toFloat() / sz.height
            if (abs(arRatio - displayArRatio) <= .2f) {
                previewSizes.add(sz)
            }
        }
        val extensionSizes = extensionCharacteristics.getExtensionSupportedSizes(
            currentExtension, SurfaceTexture::class.java
        )
        if (extensionSizes.isEmpty()) {
            Toast.makeText(
                requireActivity(), "Invalid preview extension sizes!.",
                Toast.LENGTH_SHORT
            ).show()
            requireActivity().finish()
        }

        var previewSize = extensionSizes[0]
        val supportedPreviewSizes =
            previewSizes.stream().distinct().filter { o: Size -> extensionSizes.contains(o) }
                .collect(Collectors.toList())
        if (supportedPreviewSizes.isNotEmpty()) {
            var currentDistance = Int.MAX_VALUE
            for (sz in supportedPreviewSizes) {
                val distance = abs(sz.width * sz.height - displaySize.x * displaySize.y)
                if (currentDistance > distance) {
                    currentDistance = distance
                    previewSize = sz
                }
            }
        } else {
            Log.w(
                TAG, "No overlap between supported camera and extensions preview sizes using "
                        + "first available!"
            )
        }

        return previewSize
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto():
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = cameraCaptureSession.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        cameraCaptureSession.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                fragmentPhotoBinding.viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        // Compute EXIF orientation metadata
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                            image, result, exifOrientation, imageReader.imageFormat))

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)

        submitRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE,  //表示拍单张照片
            imageReader.surface,   //输出在stillImageReader上
            false
        ) { request ->
//          不懂  TODO()
            request.apply {
                set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            }
        }
    }

    /** Helper function used to save a [CombinedCaptureResult] into a [File]
     *  保存图片
     * */
    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {
            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile(requireContext(), "jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile(requireContext(), "dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    /**
     * @param templateType request的种类，如：CameraDevice.TEMPLATE_STILL_CAPTURE
     * @param target 输出Surface
     * @param isRepeating 是否连拍
     * @param block 回调
     */
    private fun submitRequest(
        templateType: Int,
        target: Surface,
        isRepeating: Boolean,
        block: (captureRequest: CaptureRequest.Builder) -> CaptureRequest.Builder) {
        try {
            val captureBuilder = camera.createCaptureRequest(templateType)
                .apply {
                    addTarget(target)
                    if (tag != null) {
                        setTag(tag)
                    }
                    block(this)
                }
            if (isRepeating) {
                cameraExtensionSession.setRepeatingRequest(
                    captureBuilder.build(),
                    Dispatchers.IO.asExecutor(),
                    captureCallbacks
                )
            } else {
                cameraExtensionSession.capture(
                    captureBuilder.build(),
                    Dispatchers.IO.asExecutor(),
                    captureCallbacks
                )
            }
        } catch (e: CameraAccessException) {
            Toast.makeText(
                requireActivity(), "Camera failed to submit capture request!.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    override fun onDestroyView() {
        _fragmentPhotoBinding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (_fragmentPhotoBinding!!.texture.isAvailable) {
            initializeCamera()
        }
    }

    /**
     * Removes any pending operation to restart auto focus in continuous picture mode.
     */
    private fun clearPendingAutoFocusReset() {
        tapToFocusTimeoutHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Queue operation to restart auto focus in continuous picture mode.
     */
    private fun queueAutoFocusReset() {
        tapToFocusTimeoutHandler.postDelayed({
            Log.v(TAG, "Reset auto focus back to continuous picture")
            cameraExtensionSession.stopRepeating()

            submitRequest(
                CameraDevice.TEMPLATE_PREVIEW,
                previewSurface,
                true,
            ) { builder ->
                builder.apply {
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                }
            }
        }, AUTO_FOCUS_TIMEOUT_MILLIS)
    }

    /**
     * Handles the tap to focus event.
     * This will cancel any existing focus operation and restart it at the new point.
     * Note: If the device doesn't support auto focus then this operation will abort and return
     * false.
     */
    private fun tapToFocus(event: MotionEvent): Boolean {
        if (!hasAutoFocusMeteringSupport()) {
            return false
        }

        // Reset zoom -- TODO(Support zoom and tap to focus)
        zoomRatio = ZoomUtil.minZoom()

        cameraExtensionSession.stopRepeating()
        cancelPendingAutoFocus()
        startAutoFocus(meteringRectangle(event))

        return true
    }

    /**
     * Not all camera extensions have auto focus metering support.
     * Returns true if auto focus metering is supported otherwise false.
     */
    private fun hasAutoFocusMeteringSupport(): Boolean {
        if (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) == 0) {
            return false
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            val availableExtensionRequestKeys =
//                extensionCharacteristics.getAvailableCaptureRequestKeys(currentExtension)
//            return availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_TRIGGER) &&
//                    availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_MODE) &&
//                    availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_REGIONS)
//        }

        return false
    }

    /**
     * Not all camera extensions have zoom support.
     * Returns true if zoom is supported otherwise false.
     */
    private fun hasZoomSupport(): Boolean {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            val availableExtensionRequestKeys =
//                extensionCharacteristics.getAvailableCaptureRequestKeys(currentExtension)
//            return availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO)
//        }

        return false
    }


    /**
     * Translates a touch event relative to the preview surface to a region relative to the sensor.
     * Note: This operation does not account for zoom / crop and should be handled otherwise the touch
     * point won't correctly map to the sensor.
     */
    private fun meteringRectangle(event: MotionEvent): MeteringRectangle {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!

        val halfMeteringRectWidth = (METERING_RECTANGLE_SIZE * sensorSize.width()) / 2
        val halfMeteringRectHeight = (METERING_RECTANGLE_SIZE * sensorSize.height()) / 2

        // Normalize the [x,y] touch point in the view port to values in the range of [0,1]
        val normalizedPoint = floatArrayOf(event.x / previewSize.height, event.y / previewSize.width)

        // Scale and rotate the normalized point such that it maps to the sensor region
        Matrix().apply {
            postRotate(-sensorOrientation.toFloat(), 0.5f, 0.5f)
            postScale(sensorSize.width().toFloat(), sensorSize.height().toFloat())
            mapPoints(normalizedPoint)
        }

        val meteringRegion = Rect(
            (normalizedPoint[0] - halfMeteringRectWidth).toInt().coerceIn(0, sensorSize.width()),
            (normalizedPoint[1] - halfMeteringRectHeight).toInt().coerceIn(0, sensorSize.height()),
            (normalizedPoint[0] + halfMeteringRectWidth).toInt().coerceIn(0, sensorSize.width()),
            (normalizedPoint[1] + halfMeteringRectHeight).toInt().coerceIn(0, sensorSize.height())
        )

        return MeteringRectangle(meteringRegion, MeteringRectangle.METERING_WEIGHT_MAX)
    }

    private fun startAutoFocus(meteringRectangle: MeteringRectangle) {
        submitRequest(
            CameraDevice.TEMPLATE_PREVIEW,
            previewSurface,
            false,
        ) { request ->
            request.apply {
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                setTag(AUTO_FOCUS_TAG)
            }
        }
    }

    private fun cancelPendingAutoFocus() {
        clearPendingAutoFocusReset()
        submitRequest(
            CameraDevice.TEMPLATE_PREVIEW,
            previewSurface,
            true
        ) { request ->
            request.apply {
                set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            }
        }
    }

    private fun startZoom(scaleFactor: Float) {
        zoomRatio =
            (zoomRatio * scaleFactor).coerceIn(ZoomUtil.minZoom(), ZoomUtil.maxZoom(characteristics))
        Log.d(TAG, "onScale: $zoomRatio")
        submitRequest(
            CameraDevice.TEMPLATE_PREVIEW,
            previewSurface,
            true
        ) { request ->
            request.apply {
                set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            }
        }
    }

    companion object {

        private val TAG = PhotoFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3
        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000
        /** 自动对焦标签 */
        private const val AUTO_FOCUS_TAG = "auto_focus_tag"
        /** 自动对焦最大等待时间 */
        private const val AUTO_FOCUS_TIMEOUT_MILLIS = 5_000L
        /** 测量直角尺寸 */
        private const val METERING_RECTANGLE_SIZE = 0.15f

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }

        /** 扩展标签对应 */
        private fun getExtensionLabel(extension: Int): String {
            return when (extension) {
                CameraExtensionCharacteristics.EXTENSION_BEAUTY-> "BEAUTY"
                CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
                CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
                CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
                else -> "AUTO"
            }
        }

        /** 将image转换为字节串 */
        private fun getDataFromImage(image: Image): ByteArray {
            val format = image.format
            val width = image.width
            val height = image.height
            var rowStride: Int
            var pixelStride: Int
            val data: ByteArray

            // Read image data
            val planes = image.planes
            var buffer: ByteBuffer
            var offset = 0
            if (format == ImageFormat.JPEG) {
                buffer = planes[0].buffer
                data = ByteArray(buffer.limit())
                buffer.rewind()
                buffer[data]
                return data
            }
            data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
            var maxRowSize = planes[0].rowStride
            for (plane in planes) {
                if (maxRowSize < plane.rowStride) {
                    maxRowSize = plane.rowStride
                }
            }
            val rowData = ByteArray(maxRowSize)
            for (i in planes.indices) {
                buffer = planes[i].buffer
                rowStride = planes[i].rowStride
                pixelStride = planes[i].pixelStride
                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
                val w = if (i == 0) width else width / 2
                val h = if (i == 0) height else height / 2
                for (row in 0 until h) {
                    val bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8
                    var length: Int
                    if (pixelStride == bytesPerPixel) {
                        // Special case: optimized read of the entire row
                        length = w * bytesPerPixel
                        buffer[data, offset, length]
                        offset += length
                    } else {
                        // Generic case: should work for any pixelStride but slower.
                        // Use intermediate buffer to avoid read byte-by-byte from
                        // DirectByteBuffer, which is very bad for performance
                        length = (w - 1) * pixelStride + bytesPerPixel
                        buffer[rowData, 0, length]
                        for (col in 0 until w) {
                            data[offset++] = rowData[col * pixelStride]
                        }
                    }
                    // Advance buffer the remainder of the row stride
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length)
                    }
                }
                buffer.rewind()
            }
            return data
        }
    }
}
