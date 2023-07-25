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
        //初始化
        cameraHelper = CameraHelper(requireContext(),requireActivity())
        glRenderView = fragmentCameraBinding.renderView!!

        val myRenderer = MyRenderer(glRenderView,cameraHelper)
        glRenderView.setRenderer(myRenderer)
        glRenderView.setOnRecordListener(this)

        //一些功能按钮
        fragmentCameraBinding.apply {
            beauty!!.setOnClickListener{
                glRenderView.enableBeauty(beauty.isChecked)
            }
            bigEye!!.setOnClickListener {
                glRenderView.enableBigEye(bigEye.isChecked)
            }
            stick!!.setOnClickListener {
                glRenderView.enableStick(stick.isChecked)
            }
            captureModeSwitch!!.setOnClickListener {
                if (captureModeSwitch.isChecked){
                    cameraButton!!.setCaptureMode(CameraButton.CaptureMode.VIDEO)
                }else{
                    cameraButton!!.setCaptureMode(CameraButton.CaptureMode.PHOTO)
                }
            }
            rgSpeed!!.forEachIndexed { id,view ->
                view.setOnClickListener {
                    if (view.isActivated) glRenderView.setSpeed(GlRenderView.Speed.values()[id])
                }
            }
        }
        //切换相机白平衡模式按钮
        requireActivity().runOnUiThread{
            fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(cameraHelper.getCurrentAWB())
        }
        fragmentCameraBinding.switchButton!!.setOnClickListener { v ->
            if (v.id == R.id.switch_button) {
                requireActivity().runOnUiThread{
                    cameraHelper.switchAWB()
                    fragmentCameraBinding.switchButton!!.text = LableLib.getAWBLabel(cameraHelper.getCurrentAWB())
                }
            }
        }
        //拍摄按钮
        fragmentCameraBinding.cameraButton!!.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }
        //录制
        fragmentCameraBinding.cameraButton!!.setOnRecordListener(
            object : CameraButton.OnRecordListener{
                override fun onRecordStart() {
                    try {
                        val sdf = SimpleDateFormat("yyyyMMddHHmmss")
                        val file: File = FileUtil.createFile(
                            context, false, "opengl",
                            sdf.format(Date(System.currentTimeMillis())) + ".mp4", 1074000000
                        )
                        glRenderView.setSavePath(file.absolutePath)
                        glRenderView.startRecord()
                    } catch (e: FileUtil.NoExternalStoragePermissionException) {
                        e.printStackTrace()
                    } catch (e: FileUtil.NoExternalStorageMountedException) {
                        e.printStackTrace()
                    } catch (e: FileUtil.DirHasNoFreeSpaceException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onRecordStop() {
                    glRenderView.stopRecord()
                }
            }
        )
        //拍照
        fragmentCameraBinding.cameraButton!!.setOnCapturedListener(
            object : CameraButton.OnCapturedListener {
                override fun onCapture() {
                    // 先禁止，防止短时间重复请求
                    fragmentCameraBinding.cameraButton!!.isEnabled = false
                    glRenderView.post(animationTask)

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
                                Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
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
                        fragmentCameraBinding.cameraButton!!.post { fragmentCameraBinding.cameraButton!!.isEnabled = true }
                    }
                }
            }
        )
    }

    /** 保存图片 Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CameraHelper.Companion.CombinedCaptureResult): File = suspendCoroutine { cont ->
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
                    Log.e(CameraFragment2.TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(cameraHelper.getCharacteristics(), result.metadata)
                try {
                    val output = createFile(requireContext(), "dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(CameraFragment2.TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(CameraFragment2.TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun recordFinish(path: String?) {
//        val intent: Intent = Intent(this, SoulActivity::class.java)
//        intent.putExtra("path", path)
//        startActivity(intent)
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

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
}
