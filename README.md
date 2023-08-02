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
                }else{
                    glRenderView.setCameraMode(CameraMode.PHOTO)
                    cameraButton!!.setCaptureMode(CameraMode.PHOTO)
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
        //拍照
        fragmentCameraBinding.cameraButton!!.setOnCapturedListener(
            object : CameraButton.OnCapturedListener {
                override fun onCapture() {
                    // 先禁止，防止短时间重复请求
                    fragmentCameraBinding.cameraButton!!.isEnabled = false

                    glRenderView.post(animationTask)
                    val output = createFile(requireContext(),"jpg")
                    glRenderView.shoot(output.path)

                    fragmentCameraBinding.cameraButton!!.post { fragmentCameraBinding.cameraButton!!.isEnabled = true }
                }
            }
        )
        //录制
        fragmentCameraBinding.cameraButton!!.setOnRecordListener(
            object : CameraButton.OnRecordListener{
                override fun onRecordStart() {
                    try {
                        // TODO 和拍照的creatFile合并
                        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA)
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
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun shootFinish(path: String) {
        // Display the photo taken to user
        lifecycleScope.launch(Dispatchers.Main) {
            navController.navigate(CameraFragmentDirections
                .actionCameraToJpegViewer(path)
                .setOrientation(0)
                .setDepth(false)
            )
        }
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
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val picDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            return File(picDir, "IMG_${sdf.format(Date())}.$extension")
        }
    }
}
