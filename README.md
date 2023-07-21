class CameraFragment2: Fragment(), OnRecordListener {

    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** 支持的awb模式,以及目前的模式 */
    private val awbModes = ArrayList<Int>()
    private var currentAWB = -1
    private var currentAWBIdx = -1

    /** Flag whether we should restart preview after an extension switch. */
    private var restartPreview = false

    /** GlRenderView  */
    private lateinit var glRenderView: GlRenderView
    /** GLSurfaceView  */
    private lateinit var glSurfaceView: AutoFitGLSurfaceView
    /** SurfaceTexture  */
    private lateinit var cameraSurfaceTexture: SurfaceTexture
    /** 使用cameraSurfaceTexture初始化Surface */
    private lateinit var previewSurface: Surface

    /** Performs recording animation of flashing screen 拍摄时动画效果 */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            fragmentCameraBinding.overlay.postDelayed({
                // Remove white flash animation
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cameraHelper = CameraHelper(requireContext())
        glRenderView = fragmentCameraBinding.renderView!!
        val myRenderer = MyRenderer(glRenderView,cameraHelper)

        glRenderView.setRenderer(myRenderer)
        glRenderView.setOnRecordListener(this)

        fragmentCameraBinding.captureButton.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }

        //获取所有支持的AWB模式
        awbModes.addAll(characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)!!.asList())
        if (currentAWB == -1) {
            currentAWB = awbModes[0]
            currentAWBIdx = 0
            fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(currentAWB)
        }
        //切换相机模式
        fragmentCameraBinding.switchButton!!.setOnClickListener { v ->
            if (v.id == R.id.switch_button) {
                lifecycleScope.launch(Dispatchers.IO) {
                    currentAWBIdx = (currentAWBIdx + 1) % awbModes.size
                    currentAWB = awbModes[currentAWBIdx]
                    requireActivity().runOnUiThread {
                        fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(currentAWB)
                        restartPreview = true
                    }
                    try {
//                        cameraCaptureSession.stopRepeating()
//                        //会触发session的closed，重建preview
//                        cameraCaptureSession.close()
//                        previewRequest()    //直接重发request
                    } catch (e: Exception) {
                        Log.e(CameraFragment.TAG, "Camera failure when closing camera extension")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

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
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }
    }

    override fun recordFinish(path: String?) {
        TODO("Not yet implemented")
    }
}

