class CameraFragment :Fragment(), OnRecordListener,OnShootListener {

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
        cameraHelper = CameraHelper(requireContext())
        glRenderView = fragmentCameraBinding.renderView!!

        val myRenderer = MyRenderer(glRenderView,cameraHelper)
        glRenderView.setRenderer(myRenderer)
        glRenderView.setOnRecordListener(this)
        glRenderView.setOnShootListener(this)

//        glRenderView.holder.addCallback(object : SurfaceHolder.Callback {
//            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
//
//            override fun surfaceChanged(
//                holder: SurfaceHolder,
//                format: Int,
//                width: Int,
//                height: Int) = Unit
//
//            override fun surfaceCreated(holder: SurfaceHolder) {
//                // Selects appropriate preview size and configures view finder
//                val previewSize = getPreviewOutputSize(
////                    fragmentCameraBinding.viewFinder.display,
//                    glRenderView.display,
//                    cameraHelper.getCharacteristics(),
//                    SurfaceHolder::class.java
//                )
//                Log.d(TAG, "View finder size: ${glRenderView.width} x ${glRenderView.height}")
//                Log.d(TAG, "Selected preview size: $previewSize")
//                glRenderView.setAspectRatio(
//                    previewSize.height,
//                    previewSize.width
//                )
//
//                // To ensure that size is set, initialize camera in the view's thread
////                view.post { initializeCamera() }
//            }
//        })

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
                    glRenderView.setCameraMode(CameraMode.VIDEO)
                    cameraButton!!.setCaptureMode(CameraMode.VIDEO)
                    captureModeSwitch.text = getString(R.string.video_mode)
                }else{
                    glRenderView.setCameraMode(CameraMode.PHOTO)
                    cameraButton!!.setCaptureMode(CameraMode.PHOTO)
                    captureModeSwitch.text = getString(R.string.photo_mode)
                }
            }
            exposureSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setExposure(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            saturationSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setSaturation(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            contrastSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setContrast(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            brightSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setBrightness(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
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
        //拍照Listener
        fragmentCameraBinding.cameraButton!!.setOnCapturedListener(
            object : CameraButton.OnCapturedListener {
                override fun onCapture() {
                    // 先禁止，防止短时间重复请求
                    fragmentCameraBinding.cameraButton!!.isEnabled = false

                    glRenderView.post(animationTask)
                    try {
                        val output = createFile(requireContext(),"jpg")
                        glRenderView.shoot(output.absolutePath)
                    }catch (e: NullPointerException){
                        e.printStackTrace()
                    }

                    fragmentCameraBinding.cameraButton!!.post { fragmentCameraBinding.cameraButton!!.isEnabled = true }
                }
            }
        )
        //录制Listener
        fragmentCameraBinding.cameraButton!!.setOnRecordListener(
            object : CameraButton.OnRecordListener{
                override fun onRecordStart() {
                    try {
                        val output = createFile(requireContext(),"mp4")

                        glRenderView.setSavePath(output.absolutePath)
                        glRenderView.startRecord()
                    } catch (e: NullPointerException) {
                        e.printStackTrace()
                    }
                }

                override fun onRecordStop() {
                    glRenderView.stopRecord()
                }
            }
        )
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    /** 拍摄完成导航到图片预览 */
    override fun shootFinish(path: String) {
        Log.e(TAG, "Successfully save a photo in path:$path")
        lifecycleScope.launch(Dispatchers.Main) {
            navController.navigate(CameraFragmentDirections
                .actionCameraToJpegViewer(path)
                .setOrientation(0)
                .setDepth(false)
            )
        }
    }

    // TODO 导航到视频预览
    override fun recordFinish(path: String?) {
        Log.e(TAG, "Successfully save a video in path:$path")
    }

    companion object {
        private val TAG = "CameraFragment"

        /** 创建特定格式的file */
        private fun createFile(context: Context, extension: String): File {
            //保存在内部
            //val saveDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)!!.path

            //保存在手机DCIM中
            val rootsd = Environment.getExternalStorageDirectory()
            val saveDir = File(rootsd.absolutePath + "/DCIM")

            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            return File(saveDir, "${sdf.format(Date())}.$extension")
        }
    }
}
