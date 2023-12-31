package com.example.android.camera2.basic.openGL

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Environment
import android.util.Log
import com.example.android.camera2.basic.camera.CameraHelper
import com.example.android.camera2.basic.camera.CameraMode
import com.example.android.camera2.basic.face.FaceTracker
import com.example.android.camera2.basic.filter.*
import com.example.android.camera2.basic.record.AvcRecorder
import com.example.android.camera2.basic.util.OnRecordListener
import com.example.android.camera2.basic.util.OnShootListener
import com.example.android.camera2.basic.util.OpenGlUtils
import kotlinx.coroutines.runBlocking
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * 实现图像预处理的重要类
 * 完成纹理的获取、处理、绘制
 */
class MyRenderer (glRenderView: GlRenderView,mCameraHelper: CameraHelper): GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener,
    CameraHelper.OnPreviewSizeListener,
    CameraHelper.OnPreviewListener {
    private val TAG = "MyRenderer"
    private var glRenderView: GlRenderView? = null
    private var cameraHelper: CameraHelper? = mCameraHelper

    private var mPreviewWdith = 0
    private var mPreviewHeight = 0
    private var screenSurfaceWid = 0
    private var screenSurfaceHeight = 0
    private var screenX = 0
    private var screenY = 0

    private var avcRecorder: AvcRecorder? = null
    private var tracker: FaceTracker? = null
    private lateinit var mTextures: IntArray
    private var surfaceTexture: SurfaceTexture? = null
    private val mtx = FloatArray(16)

    private var screenFilter: ScreenFilter? = null
    private var cameraFilter: CameraFilter? = null
    private var bigEyeFilter: BigEyeFilter? = null
    private var stickerFilter: StickerFilter? = null
    private var beautyFilter: BeautifyFilter? = null
    private var saturationFilter:SaturationFilter? = null
    private var contrastFilter:ContrastFilter? = null
    private var exposureFilter:ExposureFilter? = null
    private var brightnessFilter:BrightnessFilter? = null

    private var onRecordListener: OnRecordListener? = null
    private var onShootListener: OnShootListener? = null

    private var stickEnable = false
    private var bigEyeEnable = false
    private var beautyEnable = false

    private var cameraMode = CameraMode.PHOTO
    private var isShooting = false
    private var shootPath = ""

    init{
        this.glRenderView = glRenderView
        val context = glRenderView.context

        val sdcardDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!.path
        //拷贝脸、眼部模型至sdcard
        OpenGlUtils.copyAssets2SdCard(
            context, "lbpcascade_frontalface_improved.xml",
            "$sdcardDir/lbpcascade_frontalface.xml"
        )
        OpenGlUtils.copyAssets2SdCard(
            context, "seeta_fa_v1.1.bin",
            "$sdcardDir/seeta_fa_v1.1.bin"
        )
    }

    //当Surface创建后会调用此方法
    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        mTextures = IntArray(1)
        //创建一个纹理
        GLES20.glGenTextures(mTextures.size, mTextures, 0)
        //将纹理和离屏buffer绑定
        surfaceTexture = SurfaceTexture(mTextures[0])
        //监听有新图像到来
        surfaceTexture!!.setOnFrameAvailableListener(this)

        //使用fbo 将samplerExternalOES 输入到sampler2D中
        cameraFilter = CameraFilter(glRenderView!!.context)
        //负责将图像绘制到屏幕上
        screenFilter = ScreenFilter(glRenderView!!.context)
        //负责调节饱和度,对比度
        saturationFilter = SaturationFilter(glRenderView!!.context)
        contrastFilter = ContrastFilter(glRenderView!!.context)
        exposureFilter = ExposureFilter(glRenderView!!.context)
        brightnessFilter = BrightnessFilter(glRenderView!!.context)
    }

    //有新图像便会执行
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        glRenderView!!.requestRender()
    }

    //当Surface创建成功或尺寸改变时都调用此方法
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 设置OpenGL ES视口
        cameraHelper!!.setPreviewSizeListener(this)
        cameraHelper!!.setOnPreviewListener(this)
        //打开相机和追踪器
        cameraHelper!!.openCamera(surfaceTexture!!,width,height)
        val sdcardDir = glRenderView!!.context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!.path
        tracker = FaceTracker(
            "$sdcardDir/lbpcascade_frontalface.xml",
            "$sdcardDir/seeta_fa_v1.1.bin",
            cameraHelper!!
        )
        tracker!!.startTrack()

        val scaleX = mPreviewWdith.toFloat() / width.toFloat()
        val scaleY = mPreviewHeight.toFloat() / height.toFloat()
        val max = max(scaleX, scaleY)

        screenSurfaceWid = (mPreviewWdith / max).toInt()
        screenSurfaceHeight =(mPreviewHeight / max).toInt()
        screenX = (width - (mPreviewWdith / max).toInt())
        screenY = (height - (mPreviewHeight / max).toInt())

        //prepare 传 绘制到屏幕上的宽、高、起始点的X坐标、起使点的Y坐标
        cameraFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        screenFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        saturationFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        contrastFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        exposureFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        brightnessFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)

        val eglContext = EGL14.eglGetCurrentContext()
        avcRecorder = AvcRecorder(glRenderView!!.context, mPreviewWdith, mPreviewHeight, eglContext)
        avcRecorder!!.setOnRecordListener(onRecordListener)
    }

    //每绘制一帧都会调用此方法
    override fun onDrawFrame(gl: GL10?) {
        var textureId:Int
        //清理屏幕 :告诉opengl 需要把屏幕清理成什么颜色
        GLES20.glClearColor(0.0f,0.0f,0.0f,0.0f)
        //执行上一个：glClearColor配置的屏幕颜色
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)


        // 更新SurfaceTexture，并在纹理上绘制
        surfaceTexture!!.updateTexImage()
        // 渲染预览纹理
        surfaceTexture!!.getTransformMatrix(mtx)

        //cameraFiler需要一个矩阵，是Surface和我们手机屏幕的一个坐标之间的关系
        cameraFilter!!.setMatrix(mtx)

        //获取相机的纹理 到surfaceTexture
        textureId = cameraFilter!!.onDrawFrame(mTextures[0])


        textureId = saturationFilter!!.onDrawFrame(textureId)
        textureId = contrastFilter!!.onDrawFrame(textureId)
        textureId = exposureFilter!!.onDrawFrame(textureId)
        textureId = brightnessFilter!!.onDrawFrame(textureId)

        if (beautyEnable) {
            textureId = beautyFilter!!.onDrawFrame(textureId)
        }
        // TODO tracker.mFace总是为null，导致大眼和贴纸无法正常运行
        if (bigEyeEnable) {
            bigEyeFilter!!.setFace(tracker!!.mFace)
            textureId = bigEyeFilter!!.onDrawFrame(textureId)
        }
        if (stickEnable) {
            stickerFilter!!.setFace(tracker!!.mFace)
            textureId = stickerFilter!!.onDrawFrame(textureId)
        }

        val id = screenFilter!!.onDrawFrame(textureId)
        when(cameraMode){
            CameraMode.PHOTO->{
                if (isShooting){
                    //拍照并保存
                    isShooting=false
                    val bitmap = createBitmapFromTexture(textureId)
                    runBlocking {
                        saveResult(bitmap)
                        onShootListener!!.shootFinish(shootPath)
                    }
                }
            }
            CameraMode.VIDEO->{
                //进行录制
                avcRecorder!!.encodeFrame(id, surfaceTexture!!.timestamp)
            }
        }
    }

    /** 保存图片 */
    private fun saveResult(bitmap: Bitmap) {
        try {
            val bos = BufferedOutputStream(FileOutputStream(shootPath))
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,bos)
            bos.flush()
            bos.close()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Unable to find the file", e)
            e.printStackTrace()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to write JPEG image to file", e)
            Files.deleteIfExists(File(shootPath).toPath())
            e.printStackTrace()
        }
    }

    /** 从纹理中获得Bitmap */
    private fun createBitmapFromTexture(textureId:Int): Bitmap {
        val frameBuffer = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffer, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])

        // 绑定纹理对象到FrameBuffer
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0
        )

        val snapWidth: Int = mPreviewWdith
        val snapHeight: Int = mPreviewHeight
        val size: Int = snapWidth * snapHeight
        val buf: ByteBuffer = ByteBuffer.allocateDirect(size * 4)
        buf.order(ByteOrder.nativeOrder())
        // 读取像素数据到buf
        GLES20.glReadPixels(
            0, 0, mPreviewWdith, mPreviewHeight,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            buf
        )
        val data = IntArray(size)
        buf.asIntBuffer().get(data)

        // 将RGBA格式的颜色转换为ARGB格式
        for (i in data.indices) {
            // 获取原始颜色
            val originalColor = data[i]

            // 将RGBA颜色转换为ARGB颜色
            val argbColor = (originalColor and 0xff00ff00.toInt()) or
                    ((originalColor and 0xff0000) shr 16) or
                    ((originalColor and 0xff) shl 16)

            // 更新像素数组中的颜色值
            data[i] = argbColor
        }

        // 创建一个临时的Bitmap对象
        var bitmap = Bitmap.createBitmap(snapWidth, snapHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(data, size-snapWidth, -snapWidth, 0, 0, snapWidth, snapHeight)

        // 翻转Bitmap
        val matrix = Matrix()
        matrix.preScale(1F, -1F)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, mPreviewWdith, mPreviewHeight, matrix, false)

        // 解绑FrameBuffer和纹理
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        // 释放FrameBuffer
        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)

        return bitmap
    }

    fun onSurfaceDestroy() {
        if (cameraHelper != null) {
            cameraHelper!!.closeCamera()
            cameraHelper!!.setPreviewSizeListener(null)
        }
        cameraFilter?.release()
        screenFilter?.release()
        tracker!!.stopTrack()
        tracker = null
    }

    override fun onSize(width: Int, height: Int) {
        mPreviewWdith = width
        mPreviewHeight = height
        Log.e(TAG, "mPreviewWdith:$mPreviewWdith")
        Log.e(TAG, "mPreviewHeight:$mPreviewHeight")
    }

    override fun onPreviewFrame(data: ByteArray?, len: Int) {
        if (tracker != null && (stickEnable || bigEyeEnable)) tracker!!.detector(data)
    }

    fun shoot(path: String){
        shootPath = path
        isShooting = true
    }

    fun startRecord(speed: Float, path: String?) {
        avcRecorder!!.start(speed, path)
    }

    fun stopRecord() {
        avcRecorder!!.stop()
    }

    fun setOnRecordListener(onRecordListener: OnRecordListener?) {
        this.onRecordListener = onRecordListener
    }

    fun setOnShootListener(onShootListener: OnShootListener?) {
        this.onShootListener = onShootListener
    }

    fun setCameraMode(mode: CameraMode){
        this.cameraMode = mode
    }

    fun setSaturation(saturation:Int){
        if (saturationFilter!=null){
            saturationFilter!!.setSaturation(saturation)
        }
    }

    fun setContrast(contrast:Int){
        if (contrastFilter!=null){
            contrastFilter!!.setContrast(contrast)
        }
    }

    fun setExposure(exposure:Int){
        if (exposureFilter!=null){
            exposureFilter!!.setExposure(exposure)
        }
    }

    fun setBrightness(brightness:Int){
        if (brightnessFilter!=null){
            brightnessFilter!!.setBrightness(brightness)
        }
    }

    fun enableStick(isChecked: Boolean) {
        stickEnable = isChecked
        if (isChecked) {
            stickerFilter = StickerFilter(glRenderView!!.context)
            stickerFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        } else {
            stickerFilter!!.release()
            stickerFilter = null
        }
    }

    fun enableBigEye(isChecked: Boolean) {
        bigEyeEnable = isChecked
        if (isChecked) {
            bigEyeFilter = BigEyeFilter(glRenderView!!.context)
            bigEyeFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        } else {
            bigEyeFilter!!.release()
            bigEyeFilter = null
        }
    }

    fun enableBeauty(isChecked: Boolean) {
        beautyEnable = isChecked
        if (isChecked) {
            beautyFilter = BeautifyFilter(glRenderView!!.context)
            beautyFilter!!.prepare(screenSurfaceWid, screenSurfaceHeight, screenX, screenY)
        } else {
            beautyFilter!!.release()
            beautyFilter = null
        }
    }
}