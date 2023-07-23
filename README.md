class CameraFragment :Fragment(), OnRecordListener {

    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Flag whether we should restart preview after an extension switch. */
    private var restartPreview = false

    /** GlRenderView  */
    private lateinit var glRenderView: GlRenderView
    /** CameraHelper */
    private lateinit var cameraHelper: CameraHelper

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

        cameraHelper = CameraHelper(requireContext(),requireActivity())
        glRenderView = fragmentCameraBinding.renderView!!
        val myRenderer = MyRenderer(glRenderView,cameraHelper)

        glRenderView.setRenderer(myRenderer)
        glRenderView.setOnRecordListener(this)

        fragmentCameraBinding.captureButton.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }

        requireActivity().runOnUiThread{
            fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(cameraHelper.getCurrentAWB())
        }
        //切换相机模式
        fragmentCameraBinding.switchButton!!.setOnClickListener { v ->
            if (v.id == R.id.switch_button) {
                requireActivity().runOnUiThread{
                    cameraHelper.switchAWB()
                    fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(cameraHelper.getCurrentAWB())
                }
            }
        }
        //拍摄按钮
        fragmentCameraBinding.captureButton.setOnClickListener {
            // 先禁止，防止短时间重复请求
            it.isEnabled = false
            glRenderView.post(animationTask)
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                cameraHelper.takePhoto().use { result ->
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
                        Log.d(CameraFragment2.TAG, "EXIF metadata saved: ${output.absolutePath}")
                    }

                    // Display the photo taken to user
                    lifecycleScope.launch(Dispatchers.Main) {
                        navController.navigate(CameraFragmentDirections
                            .actionCameraToJpegViewer(output.absolutePath)
                            .setOrientation(result.orientation)
                            .setDepth(
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        result.format == ImageFormat.DEPTH_JPEG))
                    }
                }

                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun recordFinish(path: String?) {
        TODO("Not yet implemented")
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName
    }
}

